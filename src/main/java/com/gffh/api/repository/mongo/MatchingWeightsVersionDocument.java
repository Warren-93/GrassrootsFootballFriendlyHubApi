package com.gffh.api.repository.mongo;

import com.gffh.api.domain.MatchingWeightsVersion;
import com.gffh.api.matching.MatchingWeights;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Flat fields (not an embedded MatchingWeights object), matching this codebase's existing Document conventions. */
@Document("matching_weights_versions")
public class MatchingWeightsVersionDocument {

    @Id
    public String id;
    public int age;
    public int availability;
    public int format;
    public int distance;
    public int homeAway;
    public int ability;
    public int ageBandTolerance;
    public double distanceFreeMiles;
    public double distanceFloor;
    public String versionNote;
    public String publishedByAdminId;
    public Instant publishedAt;
    public boolean active;

    public static MatchingWeightsVersionDocument from(MatchingWeightsVersion v) {
        MatchingWeightsVersionDocument d = new MatchingWeightsVersionDocument();
        d.id = v.id();
        MatchingWeights w = v.weights();
        d.age = w.age();
        d.availability = w.availability();
        d.format = w.format();
        d.distance = w.distance();
        d.homeAway = w.homeAway();
        d.ability = w.ability();
        d.ageBandTolerance = w.ageBandTolerance();
        d.distanceFreeMiles = w.distanceFreeMiles();
        d.distanceFloor = w.distanceFloor();
        d.versionNote = v.versionNote();
        d.publishedByAdminId = v.publishedByAdminId();
        d.publishedAt = v.publishedAt();
        d.active = v.active();
        return d;
    }

    public MatchingWeightsVersion toDomain() {
        MatchingWeights weights = new MatchingWeights(age, availability, format, distance, homeAway,
                ability, ageBandTolerance, distanceFreeMiles, distanceFloor);
        return new MatchingWeightsVersion(id, weights, versionNote, publishedByAdminId, publishedAt, active);
    }
}
