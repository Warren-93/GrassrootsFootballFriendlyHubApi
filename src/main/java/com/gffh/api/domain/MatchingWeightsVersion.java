package com.gffh.api.domain;

import com.gffh.api.matching.MatchingWeights;

import java.time.Instant;

/**
 * One published (or historical) matching configuration (ADM-08). Publishing
 * is additive - a new version is created and marked active, the previous
 * active version stays in history with {@code active=false} - so publishing
 * is inherently reversible: republish an old version's weights to revert.
 */
public record MatchingWeightsVersion(
        String id,
        MatchingWeights weights,
        String versionNote,
        String publishedByAdminId,
        Instant publishedAt,
        boolean active) {
}
