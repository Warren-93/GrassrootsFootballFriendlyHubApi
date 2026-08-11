package com.gffh.api.domain;

public enum HomeAwayPreference {
    HOME("Home"), AWAY("Away"), EITHER("Either");

    private final String label;

    HomeAwayPreference(String label) { this.label = label; }

    public String label() { return label; }

    /**
     * Compatible when a valid home/away assignment exists without either side
     * changing its stated preference. Two teams that both insist on hosting, or
     * both insist on travelling, are incompatible on this factor - but that is
     * scored as zero rather than treated as a hard exclusion, because one side
     * may still relent for a good fixture.
     */
    public boolean isCompatibleWith(HomeAwayPreference other) {
        if (this == EITHER || other == EITHER) return true;
        return this != other;
    }

    /**
     * Resolves which team hosts. Returns true if {@code this} side is home.
     * Only meaningful when the preferences are compatible; EITHER/EITHER
     * defaults to the proposing team hosting, which the composer then lets the
     * manager override.
     */
    public boolean resolvesToHome(HomeAwayPreference other) {
        if (this == HOME) return true;
        if (this == AWAY) return false;
        return other != HOME;
    }
}
