package com.gffh.api.dto;

import com.gffh.api.model.Venue;
import com.gffh.api.model.enums.PitchSurface;
import com.gffh.api.model.enums.VenueFacility;
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
            @Size(max = 10) String postcode,
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
            @Size(max = 10) String postcode,
            Double longitude,
            Double latitude,
            PitchSurface pitchSurface,
            List<VenueFacility> facilities,
            String accessNotes,
            String parkingNotes,
            String pitchNumber) {}

    /** {@code postcode} is the one entered for the venue, else one found in its address (may be null). */
    public record VenueView(
            String id, String clubId, String name, String address, String postcode, double longitude,
            double latitude, String pitchSurface, List<String> facilities, String accessNotes, String parkingNotes,
            String pitchNumber, boolean isDefault, Instant createdAt) {

        public static VenueView from(Venue v) {
            return new VenueView(v.id(), v.clubId(), v.name(), v.address(), v.displayPostcode(),
                    v.location().longitude(), v.location().latitude(),
                    v.pitchSurface() == null ? null : v.pitchSurface().name(),
                    v.facilities().stream().map(Enum::name).toList(),
                    v.accessNotes(), v.parkingNotes(), v.pitchNumber(), v.isDefault(), v.createdAt());
        }
    }

    /**
     * Where a friendly request or fixture is being played: enough to show the
     * venue and link directions ({@link Venue#directionsUrl()}), without the
     * club-management fields. Only {@code id} and {@code name} are always set.
     */
    public record VenueSummary(
            String id, String name, String address, String postcode, String pitchSurface,
            String accessNotes, String parkingNotes, Double latitude, Double longitude) {

        /** Null in, null out - a request or fixture may have no venue yet. */
        public static VenueSummary from(Venue v) {
            if (v == null) return null;
            return new VenueSummary(v.id(), v.name(), v.address(), v.displayPostcode(),
                    v.pitchSurface() == null ? null : v.pitchSurface().name(),
                    v.accessNotes(), v.parkingNotes(),
                    v.location() == null ? null : v.location().latitude(),
                    v.location() == null ? null : v.location().longitude());
        }
    }
}
