package com.gffh.api.web;

import com.gffh.api.domain.PitchSurface;
import com.gffh.api.domain.Venue;
import com.gffh.api.domain.VenueFacility;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/** Wire types for SCR-ON-04 Add first venue and SCR-PR-05/06 Venue management. */
public final class VenueDtos {

    private VenueDtos() {}

    public record CreateVenueRequest(
            @NotBlank String clubId,
            @NotBlank @Size(min = 3, max = 80) String name,
            @NotBlank String address,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            PitchSurface pitchSurface,
            List<VenueFacility> facilities,
            String accessNotes,
            String parkingNotes,
            String pitchNumber) {}

    public record UpdateVenueRequest(
            String name,
            String address,
            Double longitude,
            Double latitude,
            PitchSurface pitchSurface,
            List<VenueFacility> facilities,
            String accessNotes,
            String parkingNotes,
            String pitchNumber) {}

    public record VenueView(
            String id, String clubId, String name, String address, double longitude, double latitude,
            String pitchSurface, List<String> facilities, String accessNotes, String parkingNotes,
            String pitchNumber, boolean isDefault, Instant createdAt) {

        public static VenueView from(Venue v) {
            return new VenueView(v.id(), v.clubId(), v.name(), v.address(),
                    v.location().longitude(), v.location().latitude(),
                    v.pitchSurface() == null ? null : v.pitchSurface().name(),
                    v.facilities().stream().map(Enum::name).toList(),
                    v.accessNotes(), v.parkingNotes(), v.pitchNumber(), v.isDefault(), v.createdAt());
        }
    }
}
