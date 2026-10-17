package com.gffh.api.model.enums;



public enum AbilityLevel {
    DEVELOPMENT("Development"), INTERMEDIATE("Intermediate"), COMPETITIVE("Competitive");

    private final String label;

    AbilityLevel(String label) { this.label = label; }

    public String label() { return label; }

    public int bandsApart(AbilityLevel other) { return Math.abs(ordinal() - other.ordinal()); }
}
