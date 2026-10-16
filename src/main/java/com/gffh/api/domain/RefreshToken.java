package com.gffh.api.domain;

import java.time.Duration;
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
        Instant createdAt,
        Instant rotatedAt) {

    public boolean isUsable(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }

    /**
     * Replaced by a newer token less than {@code grace} ago, and not revoked
     * any other way. The browser may never have received the replacement: a
     * reload while the refresh was in flight drops the response, and two tabs
     * opened together both present the same cookie.
     */
    public boolean isRecentlyRotated(Instant now, Duration grace) {
        return revoked && rotatedAt != null && now.isBefore(rotatedAt.plus(grace)) && now.isBefore(expiresAt);
    }
}
