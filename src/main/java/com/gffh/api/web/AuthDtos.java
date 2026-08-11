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

    public record TokenResponse(
            String accessToken, String refreshToken, String tokenType, long expiresIn, UserView user) {
        public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, UserView.from(user));
        }
    }

    public record UserView(String id, String email, String displayName, boolean emailVerified) {
        public static UserView from(User user) {
            return new UserView(user.id(), user.email(), user.displayName(), user.emailVerified());
        }
    }
}
