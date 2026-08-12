package com.gffh.api.service;

import com.gffh.api.config.RateLimitProperties;
import com.gffh.api.domain.User;
import com.gffh.api.domain.VerificationTokenPurpose;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.web.AuthDtos;
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

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       VerificationTokenService verificationTokenService,
                       RateLimiter rateLimiter, RateLimitProperties rateLimits) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.verificationTokenService = verificationTokenService;
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
    }

    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        if (users.findByEmail(request.email()).isPresent()) {
            throw new BusinessRuleException("EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT,
                    "An account with this email already exists.");
        }
        User user = users.save(new User(null, request.email(),
                passwordEncoder.encode(request.password()), request.displayName(), false, Instant.now(), false));

        // The session is established immediately; verification is asynchronous
        // and does not block use of the app (SCR-AU-03, SCR-AU-06).
        String verificationToken = verificationTokenService.issue(user.id(), VerificationTokenPurpose.EMAIL_VERIFY, user.email());

        String accessToken = jwtService.issueAccessToken(user.id(), user.email());
        String refreshToken = refreshTokenService.issue(user.id());
        return AuthDtos.TokenResponse.withVerificationToken(
                accessToken, refreshToken, jwtService.expiresInSeconds(), user, verificationToken);
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        // Keyed on the attempted email, not the caller, so brute-forcing one
        // account is capped regardless of how many IPs it's spread across.
        if (!rateLimiter.tryConsume("auth:" + request.email().toLowerCase(), rateLimits.getAuthAttemptsPerHour())) {
            throw BusinessRuleException.rateLimited();
        }
        User user = users.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.password(), u.passwordHash()))
                .orElseThrow(() -> new BusinessRuleException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                        "Email or password is incorrect."));
        if (user.suspended()) {
            throw new BusinessRuleException("ACCOUNT_SUSPENDED", HttpStatus.FORBIDDEN,
                    "This account has been suspended.");
        }
        return issueToken(user);
    }

    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        String presented = request.refreshToken();
        String userId = refreshTokenService.userIdFor(presented)
                .orElseThrow(() -> new BusinessRuleException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                        "Your session has expired. Please sign in again."));
        User user = users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again."));

        String rotated = refreshTokenService.rotate(userId, presented)
                .orElseThrow(() -> new BusinessRuleException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                        "Your session has expired. Please sign in again."));
        String accessToken = jwtService.issueAccessToken(user.id(), user.email());
        return AuthDtos.TokenResponse.of(accessToken, rotated, jwtService.expiresInSeconds(), user);
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
                verificationTokenService.issue(user.id(), VerificationTokenPurpose.PASSWORD_RESET, user.email()));
    }

    public void confirmPasswordReset(AuthDtos.PasswordResetConfirmRequest request) {
        String userId = verificationTokenService.consume(request.token(), VerificationTokenPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new BusinessRuleException("INVALID_RESET_TOKEN", HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired."));
        User user = users.findById(userId).orElseThrow(BusinessRuleException::blocked);
        users.save(withPasswordHash(user, passwordEncoder.encode(request.newPassword())));
    }

    /** Returns null when the account is already verified - there's nothing to resend. */
    public String resendVerification(String userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND, "That account could not be found."));
        if (user.emailVerified()) return null;
        return verificationTokenService.issue(user.id(), VerificationTokenPurpose.EMAIL_VERIFY, user.email());
    }

    public void confirmVerification(AuthDtos.VerifyConfirmRequest request) {
        String userId = verificationTokenService.consume(request.token(), VerificationTokenPurpose.EMAIL_VERIFY)
                .orElseThrow(() -> new BusinessRuleException("INVALID_VERIFICATION_TOKEN", HttpStatus.BAD_REQUEST,
                        "This verification link is invalid or has expired."));
        User user = users.findById(userId).orElseThrow(BusinessRuleException::blocked);
        users.save(withEmailVerified(user));
    }

    private AuthDtos.TokenResponse issueToken(User user) {
        String accessToken = jwtService.issueAccessToken(user.id(), user.email());
        String refreshToken = refreshTokenService.issue(user.id());
        return AuthDtos.TokenResponse.of(accessToken, refreshToken, jwtService.expiresInSeconds(), user);
    }

    private static User withPasswordHash(User user, String passwordHash) {
        return new User(user.id(), user.email(), passwordHash, user.displayName(),
                user.emailVerified(), user.createdAt(), user.suspended());
    }

    private static User withEmailVerified(User user) {
        return new User(user.id(), user.email(), user.passwordHash(), user.displayName(),
                true, user.createdAt(), user.suspended());
    }
}
