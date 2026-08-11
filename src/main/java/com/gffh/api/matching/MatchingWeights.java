package com.gffh.api.matching;

/**
 * Factor weights for the matching engine, per Technical Specification section 13.
 *
 * <p>Weights are a runtime value rather than a constant so that they can be
 * retuned through the administration dashboard (ADM-08) and published without a
 * mobile release. The API returns the weights alongside every score so that the
 * match explanation screen (SCR-FF-06) renders the weighting actually applied,
 * not a hard-coded copy that could drift.
 *
 * @param ageBandTolerance how many age bands apart two teams may be and still
 *        match at all. Zero enforces the exact match required for MVP; raising
 *        it resolves open question 3 by configuration.
 * @param distanceFreeMiles distance below which no penalty applies.
 * @param distanceFloor the fraction of the distance score retained at the very
 *        edge of the travel radius, so a team at the limit is ranked lower but
 *        not effectively eliminated.
 */
public record MatchingWeights(
        int age,
        int availability,
        int format,
        int distance,
        int homeAway,
        int ability,
        int ageBandTolerance,
        double distanceFreeMiles,
        double distanceFloor) {

    public static final MatchingWeights DEFAULT =
            new MatchingWeights(25, 25, 15, 15, 10, 10, 0, 5.0, 0.15);

    public MatchingWeights {
        int total = age + availability + format + distance + homeAway + ability;
        if (total != 100) {
            throw new IllegalArgumentException("weights must total 100, got " + total);
        }
        if (ageBandTolerance < 0) {
            throw new IllegalArgumentException("age band tolerance cannot be negative");
        }
        if (distanceFloor < 0 || distanceFloor > 1) {
            throw new IllegalArgumentException("distance floor must be between 0 and 1");
        }
    }
}
