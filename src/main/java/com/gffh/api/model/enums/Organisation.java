package com.gffh.api.model.enums;

import java.util.List;

/** The Scottish FA affiliated national association a team plays under. */
public enum Organisation {
    SYFA("Scottish Youth FA (SYFA)"),
    SAFA("Scottish Amateur FA (SAFA)"),
    SWF("Scottish Women's Football (SWF)"),
    SWFA("Scottish Welfare FA (SWFA)"),
    SSFA("Scottish Schools' FA (SSFA)"),
    SJFA("Scottish Junior FA (SJFA)"),
    OTHER("Other / not affiliated");

    private final String label;

    Organisation(String label) { this.label = label; }

    public String label() { return label; }

    /**
     * The associations that govern teams of this age: youth, schools and
     * girls' football for youth age groups; amateur, welfare, junior and
     * women's football for adults and veterans. Filters the Organisation
     * options on create team, as {@link Gender#availableFor} does for gender.
     */
    public static List<Organisation> availableFor(AgeGroup ageGroup) {
        return ageGroup.isYouth()
                ? List.of(SYFA, SWF, SSFA, OTHER)
                : List.of(SAFA, SWF, SWFA, SJFA, OTHER);
    }

    public boolean isAvailableFor(AgeGroup ageGroup) {
        return availableFor(ageGroup).contains(this);
    }
}
