package com.gffh.api.domain;

import java.time.Instant;

/**
 * An internal staff account for the administration dashboard (ADM-01 to 09) -
 * entirely separate from {@link User}, which is a team-manager account.
 * Mandatory TOTP second factor: {@code totpSecret} is always present, set at
 * creation time (see {@code PlatformAdminAuthService#bootstrap}).
 */
public record PlatformAdmin(
        String id,
        String email,
        String passwordHash,
        String totpSecret,
        Instant createdAt,
        Instant lastLoginAt) {
}
