package com.gffh.api.domain;

import java.time.Instant;
import java.util.List;

/**
 * A pitch a club can offer for home fixtures. Club-scoped and shared across
 * every squad the club runs (SCR-PR-05), which is why this hangs off
 * {@code clubId} rather than {@code teamId}.
 */
public record Venue(
        String id,
        String clubId,
        String name,
        String address,
        GeoPoint location,
        PitchSurface pitchSurface,
        List<VenueFacility> facilities,
        String accessNotes,
        String parkingNotes,
        String pitchNumber,
        boolean isDefault,
        Instant createdAt) {
}
