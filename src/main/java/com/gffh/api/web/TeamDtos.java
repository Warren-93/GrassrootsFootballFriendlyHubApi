package com.gffh.api.web;

import com.gffh.api.domain.*;
import jakarta.validation.constraints.*;

import java.time.Instant;

/**
 * Wire types for team creation and management. Not part of the README's
 * "Outstanding" list by name, but required connective tissue: nothing else
 * (availability, matching, friendly requests) has a team to act on without it.
 */
public final class TeamDtos {

    private TeamDtos() {}

    /**
     * Creates the team's club in the same step when {@code clubId} is omitted -
     * the common case for a manager's first team, mirroring SCR-ON-03.
     */
    public record CreateTeamRequest(
            String clubId,
            String clubName,
            @NotBlank String name,
            String badgeUrl,
            @NotNull AgeGroup ageGroup,
            @NotNull Gender gender,
            Format format,
            @NotNull AbilityLevel abilityLevel,
            String league,
            @NotBlank String postcode,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @Min(1) @Max(50) int travelRadiusMiles,
            @NotNull HomeAwayPreference homeAwayPreference,
            String managerName,
            String contactPhone,
            String description,
            String defaultVenueId) {}

    public record UpdateTeamRequest(
            String name,
            String badgeUrl,
            AgeGroup ageGroup,
            Gender gender,
            Format format,
            AbilityLevel abilityLevel,
            String league,
            String postcode,
            Double longitude,
            Double latitude,
            Integer travelRadiusMiles,
            HomeAwayPreference homeAwayPreference,
            String managerName,
            String contactPhone,
            String description,
            String defaultVenueId) {}

    public record TeamView(
            String id, String clubId, String name, String clubName, String badgeUrl,
            String ageGroup, String gender, String format, String abilityLevel,
            String league, String postcode, double longitude, double latitude,
            int travelRadiusMiles, String homeAwayPreference, String managerName,
            String contactPhone, String description, String verification,
            String defaultVenueId, int completenessPercent, boolean archived, Instant createdAt) {

        public static TeamView from(Team t) {
            return from(t, true);
        }

        /**
         * {@code includeContact} is false when the caller doesn't manage this
         * team - contact details are only disclosed to the team's own
         * managers here, mirroring the CONFIRMED-only disclosure rule
         * {@link com.gffh.api.request.RequestStateMachine#disclosesContactDetails}
         * applies to fixtures and friendly requests.
         */
        public static TeamView from(Team t, boolean includeContact) {
            return new TeamView(t.id(), t.clubId(), t.name(), t.clubName(), t.badgeUrl(),
                    t.ageGroup().name(), t.gender().name(), t.format().name(), t.abilityLevel().name(),
                    t.league(), t.postcode(), t.location().longitude(), t.location().latitude(),
                    t.travelRadiusMiles(), t.homeAwayPreference().name(),
                    includeContact ? t.managerName() : null,
                    includeContact ? t.contactPhone() : null,
                    t.description(), t.verification().name(), t.defaultVenueId(),
                    t.completenessPercent(t.defaultVenueId() != null), t.archived(), t.createdAt());
        }
    }

    /**
     * SCR-PR-10's "Profile visibility" and "Share contact details" toggles -
     * team-level, not user-level, since it's the team that's discoverable in
     * search and disclosed on a confirmed fixture, not the account.
     */
    public record PrivacyPreferences(boolean searchVisible, boolean shareContactDetails) {
        public static PrivacyPreferences from(Team t) {
            return new PrivacyPreferences(t.searchVisible(), t.shareContactDetails());
        }
    }
}
