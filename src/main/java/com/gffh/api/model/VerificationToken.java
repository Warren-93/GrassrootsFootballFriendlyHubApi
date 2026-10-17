package com.gffh.api.model;

import com.gffh.api.model.enums.VerificationTokenPurpose;

import java.time.Instant;

/**
 * A single-use token backing the email-verification and password-reset flows
 * (SCR-AU-05, SCR-AU-06). Delivery is out of scope here - no email provider is
 * configured yet, so the token is logged rather than emailed; see AuthService.
 *
 * <p>{@code email} is the address the token was sent to. An email-verification
 * token proves ownership of that address only, so it stops applying once the
 * account changes to another one (see AuthService#confirmVerification).
 */
public record VerificationToken(
        String id,
        String userId,
        String token,
        VerificationTokenPurpose purpose,
        String email,
        Instant expiresAt,
        boolean used,
        Instant createdAt) {

    public boolean isUsable(Instant now) {
        return !used && now.isBefore(expiresAt);
    }

    /** Whether this token was sent to {@code address}. Tokens saved before addresses were recorded match nothing. */
    public boolean wasSentTo(String address) {
        return email != null && address != null && email.trim().equalsIgnoreCase(address.trim());
    }
}
