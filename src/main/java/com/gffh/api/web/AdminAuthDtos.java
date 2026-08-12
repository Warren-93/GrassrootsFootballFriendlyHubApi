package com.gffh.api.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AdminAuthDtos {

    private AdminAuthDtos() {}

    public record BootstrapRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 72) String password) {}

    /** {@code provisioningUri} is an otpauth:// URI - render as a QR code for the authenticator app. */
    public record BootstrapResponse(String adminId, String totpSecret, String provisioningUri) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record LoginStep1Response(String pendingToken) {}

    public record VerifyRequest(@NotBlank String pendingToken, @NotBlank String code) {}

    public record SessionResponse(String accessToken, String tokenType, long expiresIn, String email) {}
}
