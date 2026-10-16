package com.gffh.api.domain;

/** How far through onboarding a generated test account starts. */
public enum TestAccountKind {
    /** Signed up, email not verified. */
    NEW,
    /** Email verified, no club or team yet. */
    VERIFIED,
    /** Verified, and the only admin of its own club and manager of one team. */
    MANAGER
}
