package com.gffh.api.model;

import com.gffh.api.model.enums.AdminRole;

import java.time.Instant;

/**
 * An internal staff account for the administration dashboard (ADM-01 to 09) -
 * entirely separate from {@link User}, which is a team-manager account.
 * {@code totpSecret} is always set at creation time, but is only asked for when
 * ADMIN_TOTP_REQUIRED is on (see {@code AdminAuthService}). {@code role} is
 * null on accounts saved before roles existed, which count as ADMIN.
 */
public record PlatformAdmin(
        String id,
        String email,
        String passwordHash,
        String totpSecret,
        Instant createdAt,
        Instant lastLoginAt,
        AdminRole role) {

    public AdminRole effectiveRole() {
        return role == null ? AdminRole.ADMIN : role;
    }

    public boolean isSuperAdmin() {
        return role == AdminRole.SUPER_ADMIN;
    }

    public PlatformAdmin withLastLoginAt(Instant at) {
        return new PlatformAdmin(id, email, passwordHash, totpSecret, createdAt, at, role);
    }

    public PlatformAdmin withRole(AdminRole newRole) {
        return new PlatformAdmin(id, email, passwordHash, totpSecret, createdAt, lastLoginAt, newRole);
    }
}
