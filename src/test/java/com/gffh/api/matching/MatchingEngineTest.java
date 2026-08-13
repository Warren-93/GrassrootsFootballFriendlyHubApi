package com.gffh.api.matching;

import com.gffh.api.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The deterministic matching scenarios required by section 22 of the
 * Technical Specification. Ported from the original dependency-free
 * {@code CoreRulesHarness} - same scenarios, same fixtures, now run by
 * {@code mvn test} instead of a manual {@code main()} invocation.
 */
class MatchingEngineTest {

    /**
     * The worked example from the Product Proposal section 5: two U12 teams,
     * both 7v7, both free Saturday, 7 miles apart, similar ability, compatible
     * home/away preferences.
     */
    @Test
    @DisplayName("worked example: scores a high-band match with all six factors reported")
    void scoresTheWorkedExample() {
        Team a = team("team_a", "club_a", AgeGroup.U12, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 10, 55.7644, -4.1770);
        Team b = team("team_b", "club_b", AgeGroup.U12, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.AWAY, 10, 55.8300, -4.2600);

        List<AvailabilitySlot> aSlots = List.of(
                slot("av_a", "team_a", "2026-08-22", "09:00", "13:00",
                        HomeAwayPreference.EITHER, "venue_a"));
        List<AvailabilitySlot> bSlots = List.of(
                slot("av_b", "team_b", "2026-08-22", "10:00", "13:00",
                        HomeAwayPreference.AWAY, null));

        Optional<MatchResult> result = new MatchingEngine().score(a, aSlots, b, bSlots);

        assertTrue(result.isPresent(), "worked example produces a match");
        MatchResult m = result.orElseThrow();
        assertAll("worked example",
                () -> assertTrue(m.score() >= 85, "scores in the high band (was " + m.score() + ")"),
                () -> assertEquals("HIGH", m.band(), "reports the correct band"),
                () -> assertTrue(m.milesApart() > 5.0 && m.milesApart() < 9.0,
                        "is about 7 miles (was " + m.milesApart() + ")"),
                () -> assertEquals(6, m.factors().size(), "returns six factors"),
                () -> assertFalse(m.summaryReasons().isEmpty(), "returns reason chips"),
                () -> assertEquals(1, m.overlappingSlots().size(), "finds the overlapping slot"));
    }

    @Test
    @DisplayName("different age groups are excluded under MVP rules")
    void excludesDifferentAgeGroups() {
        Team a = team("a", "ca", AgeGroup.U12, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U14, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        Optional<MatchResult> result = new MatchingEngine()
                .score(a, List.of(saturdaySlot("a", "venue")), b, List.of(saturdaySlot("b", "venue")));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("the tighter of the two teams' travel radii bounds the match")
    void excludesBeyondTighterTravelRadius() {
        // ~35 miles apart. We would travel 50, they only 10.
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 50, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 10, 56.20, -4.30);

        Optional<MatchResult> result = new MatchingEngine()
                .score(a, List.of(saturdaySlot("a", "venue")), b, List.of(saturdaySlot("b", "venue")));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("two away-only teams cannot match - nobody can host")
    void excludesWhenNobodyCanHost() {
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.AWAY, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.AWAY, 20, 55.77, -4.18);

        List<AvailabilitySlot> aSlots = List.of(
                slot("av_a", "a", "2026-08-22", "10:00", "14:00", HomeAwayPreference.AWAY, null));
        List<AvailabilitySlot> bSlots = List.of(
                slot("av_b", "b", "2026-08-22", "10:00", "14:00", HomeAwayPreference.AWAY, null));

        assertTrue(new MatchingEngine().score(a, aSlots, b, bSlots).isEmpty());
    }

    @Test
    @DisplayName("an overlap shorter than the match duration is excluded")
    void excludesWhenOverlapTooShort() {
        // U13 needs 60 minutes; this overlap is 30.
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        List<AvailabilitySlot> aSlots = List.of(
                slot("av_a", "a", "2026-08-22", "10:00", "11:30", HomeAwayPreference.EITHER, "v1"));
        List<AvailabilitySlot> bSlots = List.of(
                slot("av_b", "b", "2026-08-22", "11:00", "13:00", HomeAwayPreference.EITHER, "v2"));

        assertTrue(new MatchingEngine().score(a, aSlots, b, bSlots).isEmpty());
    }

    @Test
    @DisplayName("teams from the same club are excluded")
    void excludesSameClub() {
        Team a = team("a", "same_club", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team b = team("b", "same_club", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        assertTrue(new MatchingEngine().score(a, List.of(saturdaySlot("a", "v1")),
                b, List.of(saturdaySlot("b", "v2"))).isEmpty());
    }

    @Test
    @DisplayName("format gap: two steps apart cannot produce a fixture, one step is scored not excluded")
    void handlesFormatGap() {
        Team a = team("a", "ca", AgeGroup.U13, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team wide = team("b", "cb", AgeGroup.U13, Format.ELEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);
        Team adjacent = team("c", "cc", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        assertTrue(new MatchingEngine().score(a, List.of(saturdaySlot("a", "v1")),
                wide, List.of(saturdaySlot("b", "v2"))).isEmpty(),
                "a two-step format gap cannot produce a fixture");
        assertTrue(new MatchingEngine().score(a, List.of(saturdaySlot("a", "v1")),
                adjacent, List.of(saturdaySlot("c", "v3"))).isPresent(),
                "a one-step format gap is scored, not excluded");
    }

    @Test
    @DisplayName("ranks candidates by score, then distance")
    void ranksByScoreThenDistance() {
        Team ours = team("ours", "c0", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 25, 55.76, -4.17);

        // near: identical profile, close. far: identical profile, further away.
        Team near = team("near", "c1", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 25, 55.77, -4.18);
        Team far = team("far", "c2", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 25, 55.95, -4.40);
        // weak: same distance as near, but mismatched format and ability.
        Team weak = team("weak", "c3", AgeGroup.U13, Format.ELEVEN_A_SIDE,
                AbilityLevel.COMPETITIVE, HomeAwayPreference.EITHER, 25, 55.77, -4.18);

        List<MatchResult> ranked = new MatchingEngine().rank(ours,
                List.of(saturdaySlot("ours", "v0")),
                List.of(
                        new MatchingEngine.Candidate(far, List.of(saturdaySlot("far", "v2"))),
                        new MatchingEngine.Candidate(weak, List.of(saturdaySlot("weak", "v3"))),
                        new MatchingEngine.Candidate(near, List.of(saturdaySlot("near", "v1")))));

        assertAll("ranking",
                () -> assertEquals(3, ranked.size(), "all three candidates are returned"),
                () -> assertEquals("near", ranked.get(0).teamId(), "the closest strong match ranks first"),
                () -> assertEquals("far", ranked.get(1).teamId(), "the distant strong match outranks the weak one"),
                () -> assertEquals("weak", ranked.get(2).teamId(), "the poorly matched team ranks last"),
                () -> assertTrue(ranked.get(0).score() - ranked.get(2).score() >= 10,
                        "the score spread is wide enough to be meaningful (was "
                                + ranked.get(0).score() + " to " + ranked.get(2).score() + ")"));
    }

    @Test
    @DisplayName("a home/away clash costs the home/away weight but still returns a match")
    void penalisesButKeepsHomeAwayClash() {
        // Both prefer home, but both have venues, so a fixture remains possible.
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.HOME, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.HOME, 20, 55.77, -4.18);

        Optional<MatchResult> result = new MatchingEngine().score(a,
                List.of(slot("av_a", "a", "2026-08-22", "10:00", "14:00", HomeAwayPreference.HOME, "v1")),
                b,
                List.of(slot("av_b", "b", "2026-08-22", "10:00", "14:00", HomeAwayPreference.HOME, "v2")));

        assertTrue(result.isPresent(), "a home/away clash still returns a match");
        MatchResult m = result.orElseThrow();
        FactorResult homeAway = m.factors().stream()
                .filter(f -> f.factor() == MatchFactor.HOME_AWAY).findFirst().orElseThrow();
        assertAll("home/away clash",
                () -> assertEquals(0.0, homeAway.ratio(), "the home/away factor scores zero"),
                () -> assertTrue(m.score() <= 90,
                        "the clash costs exactly the home/away weight (was " + m.score() + ")"));
    }

    @Test
    @DisplayName("age band tolerance: raising it admits one band apart, but youth/adult never bridge")
    void respectsAgeBandTolerance() {
        Team a = team("a", "ca", AgeGroup.U12, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        MatchingWeights tolerant = new MatchingWeights(25, 25, 15, 15, 10, 10, 1, 5.0, 0.40);

        assertTrue(new MatchingEngine().score(a, List.of(saturdaySlot("a", "v1")),
                b, List.of(saturdaySlot("b", "v2"))).isEmpty(),
                "one band apart is excluded by default");
        assertTrue(new MatchingEngine(tolerant).score(a, List.of(saturdaySlot("a", "v1")),
                b, List.of(saturdaySlot("b", "v2"))).isPresent(),
                "one band apart is admitted when tolerance is raised");

        // Youth and adult must never bridge, whatever the tolerance.
        Team adult = team("c", "cc", AgeGroup.ADULT, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);
        MatchingWeights veryTolerant = new MatchingWeights(25, 25, 15, 15, 10, 10, 99, 5.0, 0.40);
        assertTrue(new MatchingEngine(veryTolerant).score(a, List.of(saturdaySlot("a", "v1")),
                adult, List.of(saturdaySlot("c", "v3"))).isEmpty(),
                "youth and adult never bridge, whatever the tolerance");
    }

    @Test
    @DisplayName("the distance floor keeps a team at the radius edge viable, not scored into irrelevance")
    void distanceFloorKeepsDistantTeamsViable() {
        // A team at the very edge of the radius, otherwise a perfect match.
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 25, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 25, 56.06, -4.43);

        Optional<MatchResult> result = new MatchingEngine().score(a,
                List.of(saturdaySlot("a", "v1")), b, List.of(saturdaySlot("b", "v2")));

        assertTrue(result.isPresent(), "a team at the radius edge still matches");
        assertTrue(result.orElseThrow().score() >= 70,
                "and is not scored into irrelevance (was " + result.orElseThrow().score() + ")");
    }

    // ---- Fixtures -----------------------------------------------------------

    private static Team team(String id, String clubId, AgeGroup age, Format format,
                             AbilityLevel ability, HomeAwayPreference pref,
                             int radius, double lat, double lon) {
        return new Team(id, clubId, id + " FC", "Club " + clubId, null,
                age, Gender.MIXED, format, ability, "Local League",
                "G74", new GeoPoint(lon, lat), radius, pref,
                "Manager", "07000 000000", "A team", VerificationStatus.VERIFIED,
                "venue_" + id, null, Instant.now(), Instant.now(), false, false);
    }

    private static AvailabilitySlot slot(String id, String teamId, String date,
                                         String start, String end,
                                         HomeAwayPreference pref, String venueId) {
        return new AvailabilitySlot(id, teamId, LocalDate.parse(date),
                LocalTime.parse(start), LocalTime.parse(end), pref, venueId,
                Format.NINE_A_SIDE, null, AvailabilityStatus.AVAILABLE);
    }

    private static AvailabilitySlot saturdaySlot(String teamId, String venueId) {
        return slot("av_" + teamId, teamId, "2026-08-22", "10:00", "14:00",
                HomeAwayPreference.EITHER, venueId);
    }
}
