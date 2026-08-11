package com.gffh.api.domain;

import java.time.Instant;

/**
 * A single-use token backing the email-verification and password-reset flows
 * (SCR-AU-05, SCR-AU-06). Delivery is out of scope here - no email provider is
 * configured yet, so the token is logged rather than emailed; see AuthService.
 */
public record VerificationToken(
        String id,
        String userId,
        String token,
        VerificationTokenPurpose purpose,
        Instant expiresAt,
        boolean used,
        Instant createdAt) {

    public boolean isUsable(Instant now) {
        return !used && now.isBefore(expiresAt);
    }
}
