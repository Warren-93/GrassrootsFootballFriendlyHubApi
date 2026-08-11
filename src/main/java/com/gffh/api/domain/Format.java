package com.gffh.api.domain;

public enum Format {
    FIVE_A_SIDE("5v5", 5),
    SEVEN_A_SIDE("7v7", 7),
    NINE_A_SIDE("9v9", 9),
    ELEVEN_A_SIDE("11v11", 11);

    private final String label;
    private final int playersPerSide;

    Format(String label, int playersPerSide) {
        this.label = label;
        this.playersPerSide = playersPerSide;
    }

    public String label() { return label; }

    public int playersPerSide() { return playersPerSide; }

    /** Suggested on team creation, always overridable by the manager. */
    public static Format suggestedFor(AgeGroup ageGroup) {
        return switch (ageGroup) {
            case U7, U8 -> FIVE_A_SIDE;
            case U9, U10 -> SEVEN_A_SIDE;
            case U11, U12 -> NINE_A_SIDE;
            default -> ELEVEN_A_SIDE;
        };
    }

    public int stepsApart(Format other) {
        return Math.abs(ordinal() - other.ordinal());
    }
}
