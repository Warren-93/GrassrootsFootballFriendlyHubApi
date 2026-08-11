package com.gffh.api.matching;

/**
 * One factor's contribution to a compatibility score.
 *
 * @param ratio      how much of this factor was earned, 0.0 to 1.0
 * @param weight     the factor's weight out of 100
 * @param ourValue   this team's value, for the side-by-side on SCR-FF-06
 * @param theirValue the opponent's value
 * @param reason     server-generated explanation. The client renders this
 *                   string; it never composes its own, so that explanation
 *                   logic cannot drift from scoring logic.
 */
public record FactorResult(
        MatchFactor factor,
        double ratio,
        int weight,
        String ourValue,
        String theirValue,
        String reason) {

    public double points() { return ratio * weight; }

    public boolean isStrong() { return ratio >= 0.85; }
}
