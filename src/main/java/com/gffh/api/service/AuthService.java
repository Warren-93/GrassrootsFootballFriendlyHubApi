package com.gffh.api.service;

import com.gffh.api.config.RateLimitProperties;
import com.gffh.api.dto.AuthDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.User;
import com.gffh.api.model.VerificationToken;
import com.gffh.api.model.enums.OAuthProvider;
import com.gffh.api.model.enums.VerificationTokenPurpose;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.security.JwtService;
import com.gffh.api.security.OAuthTokenVerifier;
import com.gffh.api.security.RateLimiter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final VerificationTokenService verificationTokenService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;
    private final MemberInviteService memberInvites;
    private final OAuthTokenVerifier oauthTokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       VerificationTokenService verificationTokenService,
                       RateLimiter rateLimiter, RateLimitProperties rateLimits,
                       MemberInviteService memberInvites, OAuthTokenVerifier oauthTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.verificationTokenService = verificationTokenService;
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
        this.memberInvites = memberInvites;
        this.oauthTokens = oauthTokens;
    }

    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        if (users.findByEmail(request.email()).isPresent()) throw emailAlreadyRegistered();
        User user = saveUniqueEmail(new User(null, request.email(),
                passwordEncoder.encode(request.password()), request.displayName(), false, Instant.now(), false));

        // The session is established immediately; verification is asynchronous
        // and does not block use of the app (SCR-AU-03, SCR-AU-06).
        String verificationToken = verificationTokenService.issue(user.getId(), VerificationTokenPurpose.EMAIL_VERIFY, user.getEmail());

        RefreshTokenService.Issued refreshToken = refreshTokenService.issue(user.getId());
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), refreshToken.id());
        return AuthDtos.TokenResponse.withVerificationToken(
                accessToken, refreshToken.token(), jwtService.expiresInSeconds(), user,
                verificationTokenService.forResponse(verificationToken));
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        requireWithinAuthLimit(request.email());
        User user = users.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> {
                    rateLimiter.record(authLimitKey(request.email()));
                    return new BusinessRuleException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                            "Email or password is incorrect.");
                });
        requireNotSuspended(user);
        return issueToken(user);
    }

    /**
     * Google or Apple sign-in. The provider has proved the caller owns the
     * address, so an existing account with it is signed in, and otherwise a
     * verified account is created with no password - password sign-in then
     * fails until they use "Forgot password". Either way any team invites
     * waiting for the address are accepted. Shares login's rate limit and
     * suspension check.
     *
     * <p>An existing account that wasn't verified yet is verified now, but
     * loses its password and its sessions: nobody had proved they owned the
     * address when that password was set, so it may belong to someone who
     * registered it first to wait for the owner to verify it.
     */
    public AuthDtos.TokenResponse signInWith(OAuthProvider provider, AuthDtos.OAuthSignInRequest request) {
        OAuthTokenVerifier.Identity identity = oauthTokens.verify(provider, request.idToken());
        requireWithinAuthLimit(identity.email());

        boolean created = false;
        User user = users.findByEmail(identity.email()).orElse(null);
        if (user == null) {
            try {
                user = users.save(new User(null, identity.email(), null,
                        identity.displayName(request.displayName()), true, Instant.now(), false));
                created = true;
            } catch (DuplicateKeyException e) {
                // A double tap raced us to creating the account: sign in to that one.
                user = users.findByEmail(identity.email()).orElseThrow(() -> e);
            }
        }
        requireNotSuspended(user);
        if (!user.isEmailVerified()) {
            refreshTokenService.revokeAll(user.getId());
            user = users.save(new User(user.getId(), user.getEmail(), null, user.getDisplayName(),
                    true, user.getCreatedAt(), user.isSuspended()));
        }
        memberInvites.acceptPendingInvites(user);
        return issueToken(user).withNewUser(created);
    }

    public AuthDtos.TokenResponse refresh(String presented) {
        if (presented == null || presented.isBlank()) {
            throw new BusinessRuleException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                    "Your session has expired. Please sign in again.");
        }
        String userId = refreshTokenService.userIdFor(presented)
                .orElseThrow(() -> new BusinessRuleException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                        "Your session has expired. Please sign in again."));
        User user = users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again."));
        requireNotSuspended(user);

        RefreshTokenService.Issued rotated = refreshTokenService.rotate(userId, presented)
                .orElseThrow(() -> new BusinessRuleException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                        "Your session has expired. Please sign in again."));
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), rotated.id());
        return AuthDtos.TokenResponse.of(accessToken, rotated.token(), jwtService.expiresInSeconds(), user);
    }

    public void logout(String presentedRefreshToken) {
        if (presentedRefreshToken != null && !presentedRefreshToken.isBlank()) {
            refreshTokenService.revoke(presentedRefreshToken);
        }
    }

    public AuthDtos.UserView me(String userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND, "That account could not be found."));
        return AuthDtos.UserView.from(user);
    }

    /**
     * Deliberately neutral: the response is identical whether or not the
     * address is registered, so account existence cannot be probed
     * (SCR-AU-05).
     */
    public void requestPasswordReset(AuthDtos.PasswordResetRequest request) {
        users.findByEmail(request.email()).ifPresent(user ->
                verificationTokenService.issue(user.getId(), VerificationTokenPurpose.PASSWORD_RESET, user.getEmail()));
    }

    /** Signs the account out everywhere: whoever knew the old password may hold a session. */
    public void confirmPasswordReset(AuthDtos.PasswordResetConfirmRequest request) {
        String userId = verificationTokenService.consume(request.token(), VerificationTokenPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new BusinessRuleException("INVALID_RESET_TOKEN", HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired."));
        User user = users.findById(userId).orElseThrow(BusinessRuleException::blocked);
        users.save(withPasswordHash(user, passwordEncoder.encode(request.newPassword())));
        refreshTokenService.revokeAll(user.getId());
    }

    /** Neither a token nor emailSent when the account is already verified - there's nothing to resend. */
    public AuthDtos.VerificationResendResponse resendVerification(String userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND, "That account could not be found."));
        if (user.isEmailVerified()) return new AuthDtos.VerificationResendResponse(null, false);
        String token = verificationTokenService.issue(user.getId(), VerificationTokenPurpose.EMAIL_VERIFY, user.getEmail());
        return new AuthDtos.VerificationResendResponse(
                verificationTokenService.forResponse(token), verificationTokenService.emailsTokens());
    }

    /**
     * Verifies the address the link was sent to. A link sent to an address the
     * account has since changed away from verifies nothing: otherwise anyone
     * could change their email to an address they don't own, then confirm a
     * link from their own inbox.
     *
     * <p>Verifying also accepts any team invites waiting for the address
     * (SCR-PR-04), but only when the link was emailed. Without an email
     * provider the token goes straight back to whoever registered, which
     * proves nothing about the address, so invites then wait for an admin's
     * "verify email now" or a Google/Apple sign-in.
     */
    public void confirmVerification(AuthDtos.VerifyConfirmRequest request) {
        VerificationToken token = verificationTokenService
                .consumeToken(request.token(), VerificationTokenPurpose.EMAIL_VERIFY)
                .orElseThrow(AuthService::invalidVerificationLink);
        User user = users.findById(token.getUserId()).orElseThrow(BusinessRuleException::blocked);
        if (!token.wasSentTo(user.getEmail())) throw invalidVerificationLink();
        User verified = users.save(withEmailVerified(user));
        if (verificationTokenService.emailsTokens()) memberInvites.acceptPendingInvites(verified);
    }

    private static BusinessRuleException invalidVerificationLink() {
        return new BusinessRuleException("INVALID_VERIFICATION_TOKEN", HttpStatus.BAD_REQUEST,
                "This verification link is invalid or has expired.");
    }

    /**
     * Signs every other session out, keeping {@code sessionId} (the caller's
     * own, from its access token) so the device that changed the password
     * stays signed in. The clients don't expect new tokens back.
     */
    public void changePassword(String userId, String sessionId, AuthDtos.ChangePasswordRequest request) {
        User user = users.findById(userId).orElseThrow(BusinessRuleException::blocked);
        requireCurrentPassword(user, request.currentPassword());
        users.save(withPasswordHash(user, passwordEncoder.encode(request.newPassword())));
        refreshTokenService.revokeAllExcept(user.getId(), sessionId);
    }

    /**
     * Re-verification is required (SCR-AU-03/06's gate applies again), same
     * as a fresh registration - see {@link #confirmVerification}.
     */
    public AuthDtos.ChangeEmailResponse changeEmail(String userId, AuthDtos.ChangeEmailRequest request) {
        User user = users.findById(userId).orElseThrow(BusinessRuleException::blocked);
        requireCurrentPassword(user, request.currentPassword());
        if (!request.newEmail().equalsIgnoreCase(user.getEmail()) && users.findByEmail(request.newEmail()).isPresent()) {
            throw emailAlreadyRegistered();
        }
        User updated = saveUniqueEmail(withEmail(user, request.newEmail()));
        String verificationToken = verificationTokenService.issue(
                updated.getId(), VerificationTokenPurpose.EMAIL_VERIFY, updated.getEmail());
        return new AuthDtos.ChangeEmailResponse(AuthDtos.UserView.from(updated),
                verificationTokenService.forResponse(verificationToken));
    }

    /** The unique email index settles a race the findByEmail check above it can lose. */
    private User saveUniqueEmail(User user) {
        try {
            return users.save(user);
        } catch (DuplicateKeyException e) {
            throw emailAlreadyRegistered();
        }
    }

    private static BusinessRuleException emailAlreadyRegistered() {
        return new BusinessRuleException("EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT,
                "An account with this email already exists.");
    }

    // Keyed on the email, not the caller, so brute-forcing one account is
    // capped regardless of how many IPs it's spread across. Only failed
    // attempts count, so signing in often never locks anyone out.
    private void requireWithinAuthLimit(String email) {
        if (rateLimiter.isExhausted(authLimitKey(email), rateLimits.getAuthAttemptsPerHour())) {
            throw BusinessRuleException.rateLimited();
        }
    }

    private static String authLimitKey(String email) {
        return "auth:" + email.toLowerCase();
    }

    private static void requireNotSuspended(User user) {
        if (user.isSuspended()) {
            throw new BusinessRuleException("ACCOUNT_SUSPENDED", HttpStatus.FORBIDDEN,
                    "This account has been suspended.");
        }
    }

    private void requireCurrentPassword(User user, String presented) {
        if (!passwordEncoder.matches(presented, user.getPasswordHash())) {
            throw new BusinessRuleException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                    "Current password is incorrect.");
        }
    }

    private AuthDtos.TokenResponse issueToken(User user) {
        RefreshTokenService.Issued refreshToken = refreshTokenService.issue(user.getId());
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), refreshToken.id());
        return AuthDtos.TokenResponse.of(accessToken, refreshToken.token(), jwtService.expiresInSeconds(), user);
    }

    private static User withPasswordHash(User user, String passwordHash) {
        return new User(user.getId(), user.getEmail(), passwordHash, user.getDisplayName(),
                user.isEmailVerified(), user.getCreatedAt(), user.isSuspended());
    }

    private static User withEmailVerified(User user) {
        return new User(user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName(),
                true, user.getCreatedAt(), user.isSuspended());
    }

    private static User withEmail(User user, String newEmail) {
        return new User(user.getId(), newEmail, user.getPasswordHash(), user.getDisplayName(),
                false, user.getCreatedAt(), user.isSuspended());
    }
}
