package com.gffh.api.dto.admin;

import com.gffh.api.model.PlatformAdmin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AdminAccountDtos {

    private AdminAccountDtos() {}

    public record AdminAccountView(String id, String email, String role, Instant createdAt, Instant lastLoginAt) {
        public static AdminAccountView from(PlatformAdmin a) {
            return new AdminAccountView(a.getId(), a.getEmail(), a.effectiveRole().name(), a.getCreatedAt(), a.getLastLoginAt());
        }
    }

    /** The signed-in admin. {@code totpRequired} tells the site whether to ask for a code when re-confirming. */
    public record MeView(String id, String email, String role, boolean totpRequired) {}

    public record CreateAdminRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, max = 72) String password) {}

    /**
     * The TOTP fields are null when authenticator codes are turned off;
     * otherwise pass them to the new admin to add to their authenticator app.
     */
    public record CreateAdminResponse(AdminAccountView admin, String totpSecret, String provisioningUri) {}
}
