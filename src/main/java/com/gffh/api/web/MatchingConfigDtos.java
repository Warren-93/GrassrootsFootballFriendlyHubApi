package com.gffh.api.web;

import com.gffh.api.domain.MatchingWeightsVersion;
import com.gffh.api.matching.MatchingWeights;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class MatchingConfigDtos {

    private MatchingConfigDtos() {}

    public record WeightsInput(
            @NotNull @Min(0) Integer age,
            @NotNull @Min(0) Integer availability,
            @NotNull @Min(0) Integer format,
            @NotNull @Min(0) Integer distance,
            @NotNull @Min(0) Integer homeAway,
            @NotNull @Min(0) Integer ability,
            @Min(0) Integer ageBandTolerance,
            Double distanceFreeMiles,
            Double distanceFloor) {

        public MatchingWeights toWeights() {
            return new MatchingWeights(age, availability, format, distance, homeAway, ability,
                    ageBandTolerance != null ? ageBandTolerance : 0,
                    distanceFreeMiles != null ? distanceFreeMiles : 5.0,
                    distanceFloor != null ? distanceFloor : 0.15);
        }
    }

    public record PublishRequest(@NotNull WeightsInput weights, @NotBlank String versionNote) {}

    public record PreviewRequest(@NotBlank String teamId, @NotNull WeightsInput weights) {}

    public record VersionView(
            String id, int age, int availability, int format, int distance, int homeAway, int ability,
            int ageBandTolerance, double distanceFreeMiles, double distanceFloor,
            String versionNote, String publishedByAdminId, Instant publishedAt, boolean active) {

        public static VersionView from(MatchingWeightsVersion v) {
            MatchingWeights w = v.weights();
            return new VersionView(v.id(), w.age(), w.availability(), w.format(), w.distance(), w.homeAway(),
                    w.ability(), w.ageBandTolerance(), w.distanceFreeMiles(), w.distanceFloor(),
                    v.versionNote(), v.publishedByAdminId(), v.publishedAt(), v.active());
        }
    }
}
