package com.gffh.api.domain;

import java.util.List;

public enum Gender {
    BOYS("Boys"), GIRLS("Girls"), MIXED("Mixed"), MEN("Men"), WOMEN("Women");

    private final String label;

    Gender(String label) { this.label = label; }

    public String label() { return label; }

    /** Filters the gender options offered on SCR-ON-03 step 1. */
    public static List<Gender> availableFor(AgeGroup ageGroup) {
        return ageGroup.isYouth() ? List.of(BOYS, GIRLS, MIXED) : List.of(MEN, WOMEN, MIXED);
    }

    /** MIXED plays anything; otherwise the categories must match. */
    public boolean isCompatibleWith(Gender other) {
        return this == other || this == MIXED || other == MIXED;
    }
}
