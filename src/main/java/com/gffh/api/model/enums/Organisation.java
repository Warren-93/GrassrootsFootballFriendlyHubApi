package com.gffh.api.model.enums;

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
}
