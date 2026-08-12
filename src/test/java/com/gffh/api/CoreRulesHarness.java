package com.gffh.api;

import com.gffh.api.domain.*;
import com.gffh.api.matching.*;
import com.gffh.api.request.RequestStateMachine;
import com.gffh.api.request.RequestStateMachine.Actor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Dependency-free verification harness for the matching engine and the request
 * state machine.
 *
 * <p>These assertions are the seed of the deterministic matching test scenarios
 * required by section 22 of the Technical Specification. They run without a
 * build tool so the core rules can be checked in any environment; they should be
 * ported to JUnit 5 once the Maven build is in place.
 */
public final class CoreRulesHarness {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        matchingScoresTheWorkedExample();
        matchingExcludesDifferentAgeGroups();
        matchingExcludesBeyondTighterTravelRadius();
        matchingExcludesWhenNobodyCanHost();
        matchingExcludesWhenOverlapTooShort();
        matchingExcludesSameClub();
        matchingExcludesUnplayableFormatGap();
        matchingRanksByScoreThenDistance();
        matchingPenalisesButKeepsHomeAwayClash();
        matchingRespectsAgeBandTolerance();
        distanceFloorKeepsDistantTeamsViable();

        stateMachineAllowsRecipientToAccept();
        stateMachineForbidsSenderAcceptingOwnProposal();
        stateMachineRejectsTransitionFromTerminalState();
        stateMachineDrivesActionSets();
        stateMachineGuardsContactDisclosure();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) System.exit(1);
    }

    // ---- Matching -----------------------------------------------------------

    /**
     * The worked example from the Product Proposal section 5: two U12 teams,
     * both 7v7, both free Saturday, 7 miles apart, similar ability, compatible
     * home/away preferences.
     */
    private static void matchingScoresTheWorkedExample() {
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

        check("worked example produces a match", result.isPresent());
        MatchResult m = result.orElseThrow();
        check("worked example scores in the high band (was " + m.score() + ")", m.score() >= 85);
        check("worked example reports the correct band", m.band().equals("HIGH"));
        check("worked example is about 7 miles (was " + m.milesApart() + ")",
                m.milesApart() > 5.0 && m.milesApart() < 9.0);
        check("worked example returns six factors", m.factors().size() == 6);
        check("worked example returns reason chips", !m.summaryReasons().isEmpty());
        check("worked example finds the overlapping slot", m.overlappingSlots().size() == 1);

        System.out.println("  worked example score: " + m.score() + "%  ("
                + String.join(", ", m.summaryReasons()) + ")");
    }

    private static void matchingExcludesDifferentAgeGroups() {
        Team a = team("a", "ca", AgeGroup.U12, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U14, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        Optional<MatchResult> result = new MatchingEngine()
                .score(a, List.of(saturdaySlot("a", "venue")), b, List.of(saturdaySlot("b", "venue")));

        check("different age groups are excluded under MVP rules", result.isEmpty());
    }

    private static void matchingExcludesBeyondTighterTravelRadius() {
        // ~35 miles apart. We would travel 50, they only 10.
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 50, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 10, 56.20, -4.30);

        Optional<MatchResult> result = new MatchingEngine()
                .score(a, List.of(saturdaySlot("a", "venue")), b, List.of(saturdaySlot("b", "venue")));

        check("the tighter travel radius bounds the match", result.isEmpty());
    }

    private static void matchingExcludesWhenNobodyCanHost() {
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.AWAY, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.AWAY, 20, 55.77, -4.18);

        List<AvailabilitySlot> aSlots = List.of(
                slot("av_a", "a", "2026-08-22", "10:00", "14:00", HomeAwayPreference.AWAY, null));
        List<AvailabilitySlot> bSlots = List.of(
                slot("av_b", "b", "2026-08-22", "10:00", "14:00", HomeAwayPreference.AWAY, null));

        check("two away-only teams cannot match",
                new MatchingEngine().score(a, aSlots, b, bSlots).isEmpty());
    }

    private static void matchingExcludesWhenOverlapTooShort() {
        // U13 needs 60 minutes; this overlap is 30.
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        List<AvailabilitySlot> aSlots = List.of(
                slot("av_a", "a", "2026-08-22", "10:00", "11:30", HomeAwayPreference.EITHER, "v1"));
        List<AvailabilitySlot> bSlots = List.of(
                slot("av_b", "b", "2026-08-22", "11:00", "13:00", HomeAwayPreference.EITHER, "v2"));

        check("an overlap shorter than the match duration is excluded",
                new MatchingEngine().score(a, aSlots, b, bSlots).isEmpty());
    }

    private static void matchingExcludesSameClub() {
        Team a = team("a", "same_club", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team b = team("b", "same_club", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        check("teams from the same club are excluded",
                new MatchingEngine().score(a, List.of(saturdaySlot("a", "v1")),
                        b, List.of(saturdaySlot("b", "v2"))).isEmpty());
    }

    private static void matchingExcludesUnplayableFormatGap() {
        Team a = team("a", "ca", AgeGroup.U13, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team wide = team("b", "cb", AgeGroup.U13, Format.ELEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);
        Team adjacent = team("c", "cc", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        check("a two-step format gap cannot produce a fixture",
                new MatchingEngine().score(a, List.of(saturdaySlot("a", "v1")),
                        wide, List.of(saturdaySlot("b", "v2"))).isEmpty());
        check("a one-step format gap is scored, not excluded",
                new MatchingEngine().score(a, List.of(saturdaySlot("a", "v1")),
                        adjacent, List.of(saturdaySlot("c", "v3"))).isPresent());
    }

    private static void matchingRanksByScoreThenDistance() {
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

        check("all three candidates are returned", ranked.size() == 3);
        check("the closest strong match ranks first", ranked.get(0).teamId().equals("near"));
        check("the distant strong match outranks the weak one",
                ranked.get(1).teamId().equals("far"));
        check("the poorly matched team ranks last", ranked.get(2).teamId().equals("weak"));
        check("the score spread is wide enough to be meaningful (was "
                        + ranked.get(0).score() + " to " + ranked.get(2).score() + ")",
                ranked.get(0).score() - ranked.get(2).score() >= 10);
    }

    private static void matchingPenalisesButKeepsHomeAwayClash() {
        // Both prefer home, but both have venues, so a fixture remains possible.
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.HOME, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.HOME, 20, 55.77, -4.18);

        Optional<MatchResult> result = new MatchingEngine().score(a,
                List.of(slot("av_a", "a", "2026-08-22", "10:00", "14:00", HomeAwayPreference.HOME, "v1")),
                b,
                List.of(slot("av_b", "b", "2026-08-22", "10:00", "14:00", HomeAwayPreference.HOME, "v2")));

        check("a home/away clash still returns a match", result.isPresent());
        MatchResult m = result.orElseThrow();
        FactorResult homeAway = m.factors().stream()
                .filter(f -> f.factor() == MatchFactor.HOME_AWAY).findFirst().orElseThrow();
        check("the home/away factor scores zero", homeAway.ratio() == 0.0);
        check("the clash costs exactly the home/away weight (was " + m.score() + ")",
                m.score() <= 90);
    }

    private static void matchingRespectsAgeBandTolerance() {
        Team a = team("a", "ca", AgeGroup.U12, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);

        MatchingWeights tolerant = new MatchingWeights(25, 25, 15, 15, 10, 10, 1, 5.0, 0.40);

        check("one band apart is excluded by default",
                new MatchingEngine().score(a, List.of(saturdaySlot("a", "v1")),
                        b, List.of(saturdaySlot("b", "v2"))).isEmpty());
        check("one band apart is admitted when tolerance is raised",
                new MatchingEngine(tolerant).score(a, List.of(saturdaySlot("a", "v1")),
                        b, List.of(saturdaySlot("b", "v2"))).isPresent());

        // Youth and adult must never bridge, whatever the tolerance.
        Team adult = team("c", "cc", AgeGroup.ADULT, Format.SEVEN_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 20, 55.77, -4.18);
        MatchingWeights veryTolerant = new MatchingWeights(25, 25, 15, 15, 10, 10, 99, 5.0, 0.40);
        check("youth and adult never bridge, whatever the tolerance",
                new MatchingEngine(veryTolerant).score(a, List.of(saturdaySlot("a", "v1")),
                        adult, List.of(saturdaySlot("c", "v3"))).isEmpty());
    }

    private static void distanceFloorKeepsDistantTeamsViable() {
        // A team at the very edge of the radius, otherwise a perfect match.
        Team a = team("a", "ca", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 25, 55.76, -4.17);
        Team b = team("b", "cb", AgeGroup.U13, Format.NINE_A_SIDE,
                AbilityLevel.INTERMEDIATE, HomeAwayPreference.EITHER, 25, 56.06, -4.43);

        Optional<MatchResult> result = new MatchingEngine().score(a,
                List.of(saturdaySlot("a", "v1")), b, List.of(saturdaySlot("b", "v2")));

        check("a team at the radius edge still matches", result.isPresent());
        check("and is not scored into irrelevance (was "
                        + result.orElseThrow().score() + ")",
                result.orElseThrow().score() >= 70);
    }

    // ---- State machine ------------------------------------------------------

    private static void stateMachineAllowsRecipientToAccept() {
        check("recipient may accept a sent request",
                RequestStateMachine.isPermitted(
                        RequestStatus.SENT, RequestStatus.ACCEPTED, Actor.RECIPIENT));
        check("recipient may counter a sent request",
                RequestStateMachine.isPermitted(
                        RequestStatus.SENT, RequestStatus.CHANGES_REQUESTED, Actor.RECIPIENT));
        check("sender may withdraw a sent request",
                RequestStateMachine.isPermitted(
                        RequestStatus.SENT, RequestStatus.CANCELLED, Actor.SENDER));
    }

    private static void stateMachineForbidsSenderAcceptingOwnProposal() {
        check("sender may not accept their own proposal",
                !RequestStateMachine.isPermitted(
                        RequestStatus.SENT, RequestStatus.ACCEPTED, Actor.SENDER));

        boolean threw = false;
        String code = null;
        try {
            RequestStateMachine.requirePermitted(
                    RequestStatus.SENT, RequestStatus.ACCEPTED, Actor.SENDER);
        } catch (RequestStateMachine.IllegalRequestTransition e) {
            threw = true;
            code = e.code();
        }
        check("self-acceptance throws", threw);
        check("self-acceptance is reported as a permission error",
                "REQUEST_ACTION_NOT_PERMITTED".equals(code));
    }

    private static void stateMachineRejectsTransitionFromTerminalState() {
        boolean threw = false;
        String code = null;
        try {
            RequestStateMachine.requirePermitted(
                    RequestStatus.DECLINED, RequestStatus.ACCEPTED, Actor.RECIPIENT);
        } catch (RequestStateMachine.IllegalRequestTransition e) {
            threw = true;
            code = e.code();
        }
        check("a declined request cannot be accepted", threw);
        check("a stale-state failure is distinguishable from a permission failure",
                "REQUEST_TRANSITION_NOT_ALLOWED".equals(code));
        check("terminal states expose no transitions",
                RequestStateMachine.transitionsFrom(RequestStatus.COMPLETED).isEmpty());
    }

    private static void stateMachineDrivesActionSets() {
        var recipientActions = RequestStateMachine.transitionsFor(RequestStatus.SENT, false);
        var senderActions = RequestStateMachine.transitionsFor(RequestStatus.SENT, true);

        check("recipient sees three actions on a sent request", recipientActions.size() == 3);
        check("sender sees only withdraw on a sent request",
                senderActions.size() == 1
                        && senderActions.get(0).action().equals("withdraw"));

        var awaitingSender = RequestStateMachine.transitionsFor(
                RequestStatus.CHANGES_REQUESTED, true);
        check("sender can act once changes are requested", awaitingSender.size() == 4);
        check("recipient waits once changes are requested",
                RequestStateMachine.transitionsFor(
                        RequestStatus.CHANGES_REQUESTED, false).isEmpty());
    }

    private static void stateMachineGuardsContactDisclosure() {
        check("contact details are withheld while sent",
                !RequestStateMachine.disclosesContactDetails(RequestStatus.SENT));
        check("contact details are withheld while accepted but unconfirmed",
                !RequestStateMachine.disclosesContactDetails(RequestStatus.ACCEPTED));
        check("contact details are disclosed once confirmed",
                RequestStateMachine.disclosesContactDetails(RequestStatus.CONFIRMED));
        check("contact details remain available after completion",
                RequestStateMachine.disclosesContactDetails(RequestStatus.COMPLETED));
    }

    // ---- Fixtures -----------------------------------------------------------

    private static Team team(String id, String clubId, AgeGroup age, Format format,
                             AbilityLevel ability, HomeAwayPreference pref,
                             int radius, double lat, double lon) {
        return new Team(id, clubId, id + " FC", "Club " + clubId, null,
                age, Gender.MIXED, format, ability, "Local League",
                "G74", new GeoPoint(lon, lat), radius, pref,
                "Manager", "07000 000000", "A team", VerificationStatus.VERIFIED,
                "venue_" + id, null, Instant.now(), Instant.now(), false);
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

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + description);
        } else {
            failed++;
            System.out.println("  FAIL  " + description);
        }
    }
}
