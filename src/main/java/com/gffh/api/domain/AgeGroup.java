package com.gffh.api.domain;

/**
 * Age groups supported by the platform.
 *
 * <p>{@code defaultMatchMinutes} implements the duration defaults specified for
 * SCR-IN-01 in the Screen Build Specification (Volume 2, section 7).
 *
 * <p>Declaration order is significant: {@link #bandsApart(AgeGroup)} uses the
 * ordinal to express age-band tolerance. MVP requires an exact match (Technical
 * Specification section 13), but the tolerance is a configurable parameter of
 * the matching engine so that open question 3 can be resolved without a code
 * change.
 */
public enum AgeGroup {
    U7("U7", 40),
    U8("U8", 40),
    U9("U9", 40),
    U10("U10", 50),
    U11("U11", 50),
    U12("U12", 50),
    U13("U13", 60),
    U14("U14", 60),
    U15("U15", 70),
    U16("U16", 70),
    U17("U17", 80),
    U18("U18", 80),
    ADULT("Adult", 90),
    VETERANS("Veterans", 90);

    private final String label;
    private final int defaultMatchMinutes;

    AgeGroup(String label, int defaultMatchMinutes) {
        this.label = label;
        this.defaultMatchMinutes = defaultMatchMinutes;
    }

    public String label() { return label; }

    public int defaultMatchMinutes() { return defaultMatchMinutes; }

    public boolean isYouth() { return this != ADULT && this != VETERANS; }

    /**
     * Distance in age bands. Youth and adult groups never bridge, so any
     * cross-over returns {@link Integer#MAX_VALUE} and can never be admitted by
     * a tolerance setting.
     */
    public int bandsApart(AgeGroup other) {
        if (isYouth() != other.isYouth()) return Integer.MAX_VALUE;
        if (!isYouth() && this != other) return Integer.MAX_VALUE;
        return Math.abs(ordinal() - other.ordinal());
    }
}
