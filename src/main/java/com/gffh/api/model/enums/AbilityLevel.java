package com.gffh.api.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Map;

/**
 * The division a team plays in within its league. Shown to users as
 * "Division"; the stored field and API property keep the name
 * {@code abilityLevel} so existing documents and clients stay readable.
 *
 * <p>{@code rank} orders the levels for matching closeness. It is explicit
 * rather than {@code ordinal()} so the pick-list order can change without
 * changing scores.
 */
public enum AbilityLevel {
    PREMIER("Premier / top division", 5),
    DIVISION_1("Division 1 / Championship", 4),
    DIVISION_2("Division 2", 3),
    DIVISION_3_PLUS("Division 3 or lower", 2),
    NOT_IN_LEAGUE("Not in a league", 1),
    FESTIVAL("Festival / non-competitive", 0);

    private final String label;
    private final int rank;

    AbilityLevel(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() { return label; }

    public int bandsApart(AbilityLevel other) { return Math.abs(rank - other.rank); }

    /**
     * Parses a stored or submitted value, accepting the three levels used
     * before divisions (Development, Intermediate, Competitive) so older
     * documents and app builds keep working.
     */
    @JsonCreator
    public static AbilityLevel fromValue(String value) {
        if (value == null) return null;
        AbilityLevel legacy = LEGACY.get(value);
        return legacy != null ? legacy : valueOf(value);
    }

    /** The pre-division names, mapped to their replacements, for the one-off data migration. */
    public static final Map<String, AbilityLevel> LEGACY = Map.of(
            "DEVELOPMENT", DIVISION_3_PLUS,
            "INTERMEDIATE", DIVISION_2,
            "COMPETITIVE", DIVISION_1);
}
