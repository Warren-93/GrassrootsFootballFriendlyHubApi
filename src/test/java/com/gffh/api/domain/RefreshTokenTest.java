package com.gffh.api.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(60);

    private static RefreshToken token(boolean revoked, Instant rotatedAt) {
        return new RefreshToken("id", "user", "hash", NOW.plus(Duration.ofDays(30)), revoked, NOW.minusSeconds(3600), rotatedAt);
    }

    @Test
    @DisplayName("a token rotated moments ago is in its grace window")
    void recentlyRotated() {
        assertThat(token(true, NOW.minusSeconds(5)).isRecentlyRotated(NOW, GRACE)).isTrue();
    }

    @Test
    @DisplayName("the grace window ends")
    void graceEnds() {
        assertThat(token(true, NOW.minusSeconds(61)).isRecentlyRotated(NOW, GRACE)).isFalse();
    }

    @Test
    @DisplayName("a token revoked by sign-out or a password change has no grace")
    void revokedWithoutRotation() {
        assertThat(token(true, null).isRecentlyRotated(NOW, GRACE)).isFalse();
    }
}
