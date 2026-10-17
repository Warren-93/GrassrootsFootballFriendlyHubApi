package com.gffh.api.dto;

import com.gffh.api.model.User;
import com.gffh.api.security.RefreshCookies;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank String displayName) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    /**
     * Google or Apple sign-in. {@code displayName} names a brand-new account
     * when the token has no name: Apple gives the name only to the app, and
     * only the first time.
     */
    public record OAuthSignInRequest(
            @NotBlank String idToken,
            @Size(max = 100) String displayName) {}

    /** The token may be omitted when it comes from the refresh cookie instead (see RefreshCookies). */
    public record RefreshRequest(String refreshToken) {}

    public record PasswordResetRequest(@NotBlank @Email String email) {}

    public record PasswordResetConfirmRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {}

    public record VerifyConfirmRequest(@NotBlank String token) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {}

    public record ChangeEmailRequest(
            @NotBlank @Email String newEmail,
            @NotBlank String currentPassword) {}

    /**
     * {@code verificationToken} is safe to return here (unlike password
     * reset) because the caller just proved account ownership with their
     * current password - same reasoning as {@link VerificationResendResponse}.
     */
    public record ChangeEmailResponse(UserView user, String verificationToken) {}

    /**
     * {@code verificationToken} is only ever populated for the account whose
     * own credentials/session produced this response (registration, resend)
     * - never for an unauthenticated request like password reset, where
     * returning it would let anyone probe whether an email is registered.
     *
     * <p>{@code newUser} is only set by Google and Apple sign-in: true when
     * that sign-in created the account, so the client starts onboarding as it
     * does after registering. Absent for every other way of signing in.
     */
    public record TokenResponse(
            String accessToken, String refreshToken, String tokenType, long expiresIn, UserView user,
            String verificationToken, Boolean newUser) {
        public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, UserView.from(user), null, null);
        }

        public static TokenResponse withVerificationToken(String accessToken, String refreshToken, long expiresIn,
                                                            User user, String verificationToken) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, UserView.from(user),
                    verificationToken, null);
        }

        /** For the cookie flow, where the refresh token travels only in the httpOnly cookie. */
        public TokenResponse withoutRefreshToken() {
            return new TokenResponse(accessToken, null, tokenType, expiresIn, user, verificationToken, newUser);
        }

        public TokenResponse withNewUser(boolean created) {
            return new TokenResponse(accessToken, refreshToken, tokenType, expiresIn, user, verificationToken, created);
        }
    }

    /**
     * {@code verificationToken} is only set while no email provider is
     * configured; {@code emailSent} is true when a new link was emailed.
     * Both empty means the account was already verified.
     */
    public record VerificationResendResponse(String verificationToken, boolean emailSent) {}

    public record UserView(String id, String email, String displayName, boolean emailVerified) {
        public static UserView from(User user) {
            return new UserView(user.id(), user.email(), user.displayName(), user.emailVerified());
        }
    }
}
