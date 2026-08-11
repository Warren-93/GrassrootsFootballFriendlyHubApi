package com.gffh.api.domain;

import java.time.Instant;

/**
 * An opaque, rotating refresh token (Technical Specification section 7: "short-lived
 * access token plus refresh token strategy"). {@code tokenHash} is a SHA-256 digest,
 * never the raw token - a database compromise must not hand out usable credentials.
 */
public record RefreshToken(
        String id,
        String userId,
        String tokenHash,
        Instant expiresAt,
        boolean revoked,
        Instant createdAt) {

    public boolean isUsable(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }
}
