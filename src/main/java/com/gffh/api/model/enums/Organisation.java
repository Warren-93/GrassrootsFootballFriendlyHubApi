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
     * women's football for adults and veterans. Scottish Women's Football is
     * left out for boys' and men's teams. Filters the Organisation options on
     * create team, as {@link Gender#availableFor} does for gender.
     */
    public static List<Organisation> availableFor(AgeGroup ageGroup, Gender gender) {
        List<Organisation> byAge = ageGroup.isYouth()
                ? List.of(SYFA, SWF, SSFA, OTHER)
                : List.of(SAFA, SWF, SWFA, SJFA, OTHER);
        boolean maleOnly = gender == Gender.BOYS || gender == Gender.MEN;
        return maleOnly ? byAge.stream().filter(o -> o != SWF).toList() : byAge;
    }

    public boolean isAvailableFor(AgeGroup ageGroup, Gender gender) {
        return availableFor(ageGroup, gender).contains(this);
    }
}
