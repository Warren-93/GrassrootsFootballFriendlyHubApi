package com.gffh.api.matching;

/** The six scored factors, in the order they are presented on SCR-FF-06. */
public enum MatchFactor {
    AGE_GROUP("Age group"),
    AVAILABILITY("Availability"),
    FORMAT("Format"),
    DISTANCE("Distance"),
    HOME_AWAY("Home/Away"),
    ABILITY("Ability");

    private final String label;

    MatchFactor(String label) { this.label = label; }

    public String label() { return label; }
}
