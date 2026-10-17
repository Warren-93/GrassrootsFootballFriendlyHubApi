package com.gffh.api.dto;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;

/**
 * Wire types for team creation and management. Not part of the README's
 * "Outstanding" list by name, but required connective tissue: nothing else
 * (availability, matching, friendly requests) has a team to act on without it.
 */
public final class TeamDtos {

    /** A badge is a link or a small uploaded image (the web app resizes uploads to 256px). Empty clears it. */
    static final int BADGE_MAX_LENGTH = 300_000;
    static final String BADGE_PATTERN = "^$|^https?://\\S+$|^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$";


    private TeamDtos() {}

    /**
     * Creates the team's club in the same step when {@code clubId} is omitted -
     * the common case for a manager's first team, mirroring SCR-ON-03.
     */
    public record CreateTeamRequest(
            String clubId,
            String clubName,
            @NotBlank String name,
            @Size(max = BADGE_MAX_LENGTH) @Pattern(regexp = BADGE_PATTERN, message = "must be an https link or an uploaded image") String badgeUrl,
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
            @Size(max = BADGE_MAX_LENGTH) @Pattern(regexp = BADGE_PATTERN, message = "must be an https link or an uploaded image") String badgeUrl,
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

    /**
     * {@code readyForRequests} is the gate on sending and receiving friendly
     * requests (verified + has a venue); {@code profileChecklist} lists what
     * is still to do, so clients can tell a manager exactly what's missing.
     * {@code canManage} and {@code canManageClub} are only filled in by
     * {@link #mine} and are null (so omitted) everywhere else.
     */
    public record TeamView(
            String id, String clubId, String name, String clubName, String badgeUrl,
            String ageGroup, String gender, String format, String abilityLevel,
            String league, String postcode, double longitude, double latitude,
            int travelRadiusMiles, String homeAwayPreference, String managerName,
            String contactPhone, String description, String verification,
            String defaultVenueId, int completenessPercent, boolean archived, Instant createdAt,
            Boolean canManage, Boolean canManageClub, boolean readyForRequests,
            List<Team.ChecklistItem> profileChecklist) {

        /**
         * {@code includeContact} is false when the caller doesn't manage this
         * team - contact details are only disclosed to the team's own
         * managers here, mirroring the CONFIRMED-only disclosure rule
         * {@link com.gffh.api.service.RequestStateMachine#disclosesContactDetails}
         * applies to fixtures and friendly requests. The checklist is likewise
         * only for the team's own managers.
         */
        public static TeamView from(Team t, boolean includeContact, boolean hasVenue) {
            return new TeamView(t.id(), t.clubId(), t.name(), t.clubName(), t.badgeUrl(),
                    t.ageGroup().name(), t.gender().name(), t.format().name(), t.abilityLevel().name(),
                    t.league(), t.postcode(), t.location().longitude(), t.location().latitude(),
                    t.travelRadiusMiles(), t.homeAwayPreference().name(),
                    includeContact ? t.managerName() : null,
                    includeContact ? t.contactPhone() : null,
                    t.description(), t.verification().name(), t.defaultVenueId(),
                    t.completenessPercent(hasVenue), t.archived(), t.createdAt(), null, null,
                    t.readyForRequests(hasVenue),
                    includeContact ? t.profileChecklist(hasVenue) : List.of());
        }

        /**
         * GET /teams/mine: says whether the caller can manage the team, so a
         * member who joined with a code (role USER) gets a read-only app, and
         * whether they can manage its club (CLUB_ADMIN), so a team manager
         * sees the club's venues without the add, edit, make default and
         * delete they'd be refused.
         */
        public static TeamView mine(Team t, boolean canManage, boolean canManageClub, boolean hasVenue) {
            TeamView v = from(t, true, hasVenue);
            return new TeamView(v.id(), v.clubId(), v.name(), v.clubName(), v.badgeUrl(), v.ageGroup(), v.gender(),
                    v.format(), v.abilityLevel(), v.league(), v.postcode(), v.longitude(), v.latitude(),
                    v.travelRadiusMiles(), v.homeAwayPreference(), v.managerName(), v.contactPhone(),
                    v.description(), v.verification(), v.defaultVenueId(), v.completenessPercent(),
                    v.archived(), v.createdAt(), canManage, canManageClub, v.readyForRequests(),
                    v.profileChecklist());
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
