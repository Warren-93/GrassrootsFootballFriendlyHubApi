package com.gffh.api.web;

import com.gffh.api.domain.Club;
import jakarta.validation.constraints.*;

import java.time.Instant;

/** Wire types for SCR-ON-02 Create club and SCR-PR-03 Club profile. */
public final class ClubDtos {

    private ClubDtos() {}

    public record CreateClubRequest(
            @NotBlank @Size(min = 3, max = 80) String name,
            String badgeUrl,
            @NotBlank String postcode,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            String website,
            @Email String contactEmail) {}

    public record UpdateClubRequest(
            String name,
            String badgeUrl,
            String postcode,
            Double longitude,
            Double latitude,
            String website,
            @Email String contactEmail) {}

    public record ClubView(
            String id, String name, String badgeUrl, String postcode,
            Double longitude, Double latitude, String website, String contactEmail, Instant createdAt) {

        public static ClubView from(Club c) {
            return new ClubView(c.id(), c.name(), c.badgeUrl(), c.postcode(),
                    c.location() == null ? null : c.location().longitude(),
                    c.location() == null ? null : c.location().latitude(),
                    c.website(), c.contactEmail(), c.createdAt());
        }
    }
}
