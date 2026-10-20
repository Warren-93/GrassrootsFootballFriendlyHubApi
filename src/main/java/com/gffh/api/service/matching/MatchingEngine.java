package com.gffh.api.service.matching;

import com.gffh.api.model.AvailabilitySlot;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.HomeAwayPreference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Scores potential opponents and explains why.
 *
 * <p>Runs entirely server-side, per the architectural principle in the Technical
 * Specification: scoring rules must be able to evolve without a mobile release.
 * The engine is deliberately free of Spring annotations and of any persistence
 * or web dependency, so it can be unit tested in isolation and so the scoring
 * rules stay readable as rules rather than as plumbing.
 *
 * <p>Two categories of rule exist:
 * <ul>
 *   <li><b>Exclusions</b> - the only hard rules: the same team or club, a
 *       different age group, or a different gender category. These return no
 *       result rather than a low score.</li>
 *   <li><b>Scored factors</b> - the six weighted factors, each producing a ratio
 *       and a human-readable reason.</li>
 * </ul>
 */
public final class MatchingEngine {

    private final MatchingWeights weights;

    public MatchingEngine() {
        this(MatchingWeights.DEFAULT);
    }

    public MatchingEngine(MatchingWeights weights) {
        this.weights = weights;
    }

    public MatchingWeights weights() {
        return weights;
    }

    /**
     * Scores and ranks a candidate set.
     *
     * @param ourTeam       the searching team
     * @param ourSlots      the searching team's published, bookable availability
     * @param candidates    candidate teams with their published availability
     * @return matches in presentation order, excluded candidates omitted
     */
    public List<MatchResult> rank(Team ourTeam,
                                  List<AvailabilitySlot> ourSlots,
                                  List<Candidate> candidates) {
        List<MatchResult> results = new ArrayList<>();
        for (Candidate candidate : candidates) {
            score(ourTeam, ourSlots, candidate.team(), candidate.slots())
                    .filter(match -> !match.overlappingSlots().isEmpty())
                    .ifPresent(results::add);
        }
        results.sort(MatchResult.RANKING);
        return results;
    }

    /**
     * Scores a single potential opponent.
     *
     * <p>Only the team's identity, age group and gender can exclude a
     * candidate. Everything else - shared dates, format, distance, ability,
     * home/away - is scored, so a brand-new team with no availability yet
     * still sees every team it could play, ranked by fit, rather than an empty
     * list.
     *
     * @return empty when an exclusion applies and no fixture is possible
     */
    public Optional<MatchResult> score(Team ourTeam,
                                       List<AvailabilitySlot> ourSlots,
                                       Team theirTeam,
                                       List<AvailabilitySlot> theirSlots) {

        // ---- Exclusions -----------------------------------------------------

        if (ourTeam.id().equals(theirTeam.id())) return Optional.empty();
        if (ourTeam.clubId().equals(theirTeam.clubId())) return Optional.empty();

        // Gender categories are a hard constraint, never a scored preference,
        // and must match exactly.
        if (!ourTeam.gender().isCompatibleWith(theirTeam.gender())) return Optional.empty();

        int ageBands = ourTeam.ageGroup().bandsApart(theirTeam.ageGroup());
        if (ageBands > weights.ageBandTolerance()) return Optional.empty();

        // ---- Scored factors -------------------------------------------------

        // Distance, format and availability used to exclude; they now only
        // rank. The travel radius still shapes the distance score, so a team
        // beyond it sits lower in the list rather than vanishing from it.
        double miles = ourTeam.location().milesTo(theirTeam.location());
        int radiusLimit = Math.min(ourTeam.travelRadiusMiles(), theirTeam.travelRadiusMiles());

        int requiredMinutes = ourTeam.ageGroup().defaultMatchMinutes();
        List<MatchResult.SlotOverlap> overlaps =
                findOverlaps(ourSlots, theirSlots, requiredMinutes);

        List<FactorResult> factors = List.of(
                scoreAge(ourTeam, theirTeam, ageBands),
                scoreAvailability(overlaps, requiredMinutes),
                scoreFormat(ourTeam, theirTeam),
                scoreDistance(miles, radiusLimit),
                scoreHomeAway(ourTeam, theirTeam),
                scoreAbility(ourTeam, theirTeam)
        );

        double points = factors.stream().mapToDouble(FactorResult::points).sum();
        int score = (int) Math.round(points);

        return Optional.of(new MatchResult(
                theirTeam.id(),
                score,
                factors,
                overlaps,
                round(miles),
                theirTeam.isVerified(),
                weights));
    }

    // ---- Factor scoring -----------------------------------------------------

    private FactorResult scoreAge(Team ours, Team theirs, int bandsApart) {
        double ratio = bandsApart == 0 ? 1.0 : Math.max(0, 1.0 - (bandsApart * 0.5));
        String reason = bandsApart == 0
                ? "Same age group"
                : bandsApart + (bandsApart == 1 ? " age band apart" : " age bands apart");
        return new FactorResult(MatchFactor.AGE_GROUP, ratio, weights.age(),
                ours.ageGroup().label(), theirs.ageGroup().label(), reason);
    }

    /**
     * Availability scores on how much room the overlap leaves beyond the match
     * itself. A window that only just fits is workable but fragile; one with an
     * hour of slack accommodates a late kick-off or a long warm-up.
     */
    private FactorResult scoreAvailability(List<MatchResult.SlotOverlap> overlaps,
                                           int requiredMinutes) {
        if (overlaps.isEmpty()) {
            // Not an exclusion: either side can still add dates, and a new
            // team needs to see who is out there before it publishes any.
            return new FactorResult(MatchFactor.AVAILABILITY, 0.0, weights.availability(),
                    "No shared dates", "No shared dates", "No shared dates yet");
        }
        MatchResult.SlotOverlap best = overlaps.get(0);
        int slack = best.overlapMinutes() - requiredMinutes;
        double ratio = slack <= 0 ? 0.60
                : Math.min(1.0, 0.60 + (slack / 90.0) * 0.40);

        String window = best.ours().overlapStartWith(best.theirs()).toString();
        String reason = overlaps.size() > 1
                ? "Both free on " + overlaps.size() + " dates"
                : "Both free from " + window;

        return new FactorResult(MatchFactor.AVAILABILITY, ratio, weights.availability(),
                describeSlot(best.ours()), describeSlot(best.theirs()), reason);
    }

    private FactorResult scoreFormat(Team ours, Team theirs) {
        // One step apart is negotiable; further apart is shown but scores
        // nothing, so the teams can still talk if they want to.
        int steps = ours.format().stepsApart(theirs.format());
        double ratio = steps == 0 ? 1.0 : steps == 1 ? 0.40 : 0.0;
        String reason = steps == 0
                ? "Same format (" + ours.format().label() + ")"
                : "Formats differ - " + ours.format().label()
                        + " v " + theirs.format().label();
        return new FactorResult(MatchFactor.FORMAT, ratio, weights.format(),
                ours.format().label(), theirs.format().label(), reason);
    }

    /**
     * No penalty within the free radius, then linear decay to a floor at the
     * edge of the travel limit. The floor matters: without it, a team at 95% of
     * the radius scores near zero on 15% of the total and effectively vanishes,
     * even though it is a perfectly playable fixture.
     */
    private FactorResult scoreDistance(double miles, int radiusLimit) {
        double ratio;
        if (miles <= weights.distanceFreeMiles()) {
            ratio = 1.0;
        } else if (radiusLimit <= weights.distanceFreeMiles()) {
            ratio = 1.0;
        } else {
            double span = radiusLimit - weights.distanceFreeMiles();
            double travelled = miles - weights.distanceFreeMiles();
            double decay = Math.min(1.0, travelled / span);
            ratio = 1.0 - decay * (1.0 - weights.distanceFloor());
        }
        String rounded = String.format("%.0f miles", miles);
        return new FactorResult(MatchFactor.DISTANCE, ratio, weights.distance(),
                "Within " + radiusLimit + " miles", rounded,
                rounded + " apart");
    }

    private FactorResult scoreHomeAway(Team ours, Team theirs) {
        HomeAwayPreference a = ours.homeAwayPreference();
        HomeAwayPreference b = theirs.homeAwayPreference();
        boolean compatible = a.isCompatibleWith(b);

        // Both insisting on the same thing scores zero but is not an exclusion:
        // one side may still relent for an otherwise good fixture.
        double ratio = compatible ? 1.0 : 0.0;
        String reason = compatible
                ? (a == HomeAwayPreference.EITHER && b == HomeAwayPreference.EITHER
                    ? "Either team can host"
                    : "Home/away preferences fit")
                : "You both prefer to play " + a.label().toLowerCase();

        return new FactorResult(MatchFactor.HOME_AWAY, ratio, weights.homeAway(),
                a.label(), b.label(), reason);
    }

    private FactorResult scoreAbility(Team ours, Team theirs) {
        int bands = ours.abilityLevel().bandsApart(theirs.abilityLevel());
        double ratio = switch (bands) {
            case 0 -> 1.0;
            case 1 -> 0.5;
            default -> 0.0;
        };
        String reason = switch (bands) {
            case 0 -> "Similar ability";
            case 1 -> "Slightly different ability";
            default -> "Ability levels differ";
        };
        return new FactorResult(MatchFactor.ABILITY, ratio, weights.ability(),
                ours.abilityLevel().label(), theirs.abilityLevel().label(), reason);
    }

    // ---- Helpers ------------------------------------------------------------

    /**
     * All slot pairs that overlap by at least the match duration, best first.
     * Only bookable slots participate: a slot already reserved against another
     * negotiation must not generate a second proposal.
     */
    private List<MatchResult.SlotOverlap> findOverlaps(List<AvailabilitySlot> ourSlots,
                                                       List<AvailabilitySlot> theirSlots,
                                                       int requiredMinutes) {
        List<MatchResult.SlotOverlap> overlaps = new ArrayList<>();
        for (AvailabilitySlot ours : ourSlots) {
            if (!ours.isBookable()) continue;
            for (AvailabilitySlot theirs : theirSlots) {
                if (!theirs.isBookable()) continue;
                if (!hostingIsPossible(ours, theirs)) continue;

                int overlap = ours.overlapMinutesWith(theirs);
                if (overlap >= requiredMinutes) {
                    overlaps.add(new MatchResult.SlotOverlap(ours, theirs, overlap));
                }
            }
        }
        overlaps.sort(Comparator
                .comparing((MatchResult.SlotOverlap o) -> o.ours().date())
                .thenComparing(o -> o.ours().startTime()));
        return overlaps;
    }

    /**
     * A pair of slots is only usable if somebody can host it. Two slots that
     * both say "away only", or a home slot with no venue attached, cannot
     * produce a fixture.
     */
    private boolean hostingIsPossible(AvailabilitySlot ours, AvailabilitySlot theirs) {
        boolean weCanHost = ours.homeAwayPreference() != HomeAwayPreference.AWAY
                && ours.venueId() != null;
        boolean theyCanHost = theirs.homeAwayPreference() != HomeAwayPreference.AWAY
                && theirs.venueId() != null;
        return weCanHost || theyCanHost;
    }

    private static String describeSlot(AvailabilitySlot slot) {
        return slot.date() + " " + slot.startTime() + "-" + slot.endTime();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** A candidate team together with its published availability. */
    public record Candidate(Team team, List<AvailabilitySlot> slots) {}
}

