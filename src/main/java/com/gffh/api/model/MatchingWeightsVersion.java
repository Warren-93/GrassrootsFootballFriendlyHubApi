package com.gffh.api.model;

import com.gffh.api.service.matching.MatchingWeights;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One published (or historical) matching configuration (ADM-08). Publishing
 * is additive - a new version is created and marked active, the previous
 * active version stays in history with {@code active=false} - so publishing
 * is inherently reversible: republish an old version's weights to revert.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingWeightsVersion {

    private String id;
    private MatchingWeights weights;
    private String versionNote;
    private String publishedByAdminId;
    private Instant publishedAt;
    private boolean active;
}
