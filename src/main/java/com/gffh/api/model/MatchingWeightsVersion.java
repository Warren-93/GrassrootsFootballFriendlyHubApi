package com.gffh.api.model;

import com.gffh.api.service.matching.MatchingWeights;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Unwrapped;

import java.time.Instant;

/**
 * One published (or historical) matching configuration (ADM-08). Publishing
 * is additive - a new version is created and marked active, the previous
 * active version stays in history with {@code active=false} - so publishing
 * is inherently reversible: republish an old version's weights to revert.
 */
@Document("matching_weights_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingWeightsVersion {

    @Id
    private String id;
    /** Stored flat on the document (age, availability, format, ...), not as a nested object. */
    @Unwrapped.Nullable
    private MatchingWeights weights;
    private String versionNote;
    private String publishedByAdminId;
    @CreatedDate
    private Instant publishedAt;
    private boolean active;
}
