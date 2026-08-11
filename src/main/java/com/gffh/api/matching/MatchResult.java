package com.gffh.api.matching;

import com.gffh.api.domain.AvailabilitySlot;
import java.util.Comparator;
import java.util.List;

/**
 * A scored potential opponent.
 *
 * @param overlappingSlots the pairs of slots that make this fixture possible,
 *        used to pre-fill the invitation composer (SCR-IN-01) and to draw the
 *        overlap timeline on the opponent profile (SCR-FF-05).
 */
public record MatchResult(
        String teamId,
        int score,
        List<FactorResult> factors,
        List<SlotOverlap> overlappingSlots,
        double milesApart,
        boolean verified,
        MatchingWeights weights) {

    /**
     * Ranking order for SCR-FF-03: score descending, then distance ascending,
     * then verified teams before unverified.
     */
    public static final Comparator<MatchResult> RANKING =
            Comparator.comparingInt(MatchResult::score).reversed()
                    .thenComparingDouble(MatchResult::milesApart)
                    .thenComparing(MatchResult::verified, Comparator.reverseOrder());

    /** Band drives the score chip colour and label. */
    public String band() {
        if (score >= 85) return "HIGH";
        if (score >= 70) return "MID";
        return "LOW";
    }

    /**
     * The reason strings shown as chips on the result card. Strong factors
     * first, capped at three - the full set is on the explanation screen.
     */
    public List<String> summaryReasons() {
        return factors.stream()
                .filter(FactorResult::isStrong)
                .map(FactorResult::reason)
                .limit(3)
                .toList();
    }

    public SlotOverlap earliestOverlap() {
        return overlappingSlots.isEmpty() ? null : overlappingSlots.get(0);
    }

    /** A pair of availability slots that can host the same fixture. */
    public record SlotOverlap(AvailabilitySlot ours, AvailabilitySlot theirs, int overlapMinutes) {}
}
