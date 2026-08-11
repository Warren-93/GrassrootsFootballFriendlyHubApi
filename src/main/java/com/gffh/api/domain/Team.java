package com.gffh.api.domain;

import java.time.Instant;

/**
 * A squad. The matching engine operates entirely on this type plus the team's
 * published availability.
 *
 * <p>{@code contactPhone} is populated from persistence but must never reach a
 * client except through a CONFIRMED fixture. See {@code TeamSummaryDto} and
 * {@code FixtureDetailDto} for the two projections that enforce this.
 */
public record Team(
        String id,
        String clubId,
        String name,
        String clubName,
        String badgeUrl,
        AgeGroup ageGroup,
        Gender gender,
        Format format,
        AbilityLevel abilityLevel,
        String league,
        String postcode,
        GeoPoint location,
        int travelRadiusMiles,
        HomeAwayPreference homeAwayPreference,
        String managerName,
        String contactPhone,
        String description,
        VerificationStatus verification,
        String defaultVenueId,
        Instant firstFixtureConfirmedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isVerified() { return verification == VerificationStatus.VERIFIED; }

    /**
     * Age group and format lock once match history exists, so that a team
     * cannot retrospectively change what its past fixtures meant (SCR-PR-02).
     */
    public boolean matchingFieldsLocked() { return firstFixtureConfirmedAt != null; }

    /**
     * Drives the 80% gate on sending invitations (Volume 2, section 11).
     * Weighted by contribution to match quality rather than by field count, so
     * completing a decorative field cannot unlock sending on its own.
     */
    public int completenessPercent(boolean hasVenue) {
        int score = 0;
        int total = 0;

        total += 20; if (notBlank(name)) score += 20;
        total += 15; if (notBlank(postcode)) score += 15;
        total += 15; if (notBlank(managerName)) score += 15;
        total += 15; if (hasVenue || homeAwayPreference == HomeAwayPreference.AWAY) score += 15;
        total += 10; if (notBlank(badgeUrl)) score += 10;
        total += 10; if (notBlank(description)) score += 10;
        total += 10; if (notBlank(league)) score += 10;
        total += 5;  if (notBlank(contactPhone)) score += 5;

        return total == 0 ? 0 : (score * 100) / total;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
