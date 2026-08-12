package com.gffh.api.web;

import com.gffh.api.domain.User;
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

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record PasswordResetRequest(@NotBlank @Email String email) {}

    public record PasswordResetConfirmRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 10, max = 72) String newPassword) {}

    public record VerifyConfirmRequest(@NotBlank String token) {}

    /**
     * {@code verificationToken} is only ever populated for the account whose
     * own credentials/session produced this response (registration, resend)
     * - never for an unauthenticated request like password reset, where
     * returning it would let anyone probe whether an email is registered.
     */
    public record TokenResponse(
            String accessToken, String refreshToken, String tokenType, long expiresIn, UserView user,
            String verificationToken) {
        public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, UserView.from(user), null);
        }

        public static TokenResponse withVerificationToken(String accessToken, String refreshToken, long expiresIn,
                                                            User user, String verificationToken) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, UserView.from(user), verificationToken);
        }
    }

    public record VerificationResendResponse(String verificationToken) {}

    public record UserView(String id, String email, String displayName, boolean emailVerified) {
        public static UserView from(User user) {
            return new UserView(user.id(), user.email(), user.displayName(), user.emailVerified());
        }
    }
}
