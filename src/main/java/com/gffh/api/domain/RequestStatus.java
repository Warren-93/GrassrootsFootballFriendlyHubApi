package com.gffh.api.domain;

/** Friendly request states - Technical Specification section 14. */
public enum RequestStatus {
    DRAFT,
    SENT,
    CHANGES_REQUESTED,
    UPDATED,
    ACCEPTED,
    CONFIRMED,
    DECLINED,
    CANCELLED,
    COMPLETED;

    /** Still under negotiation. */
    public boolean isOpen() {
        return this == SENT || this == CHANGES_REQUESTED || this == UPDATED;
    }

    public boolean isTerminal() {
        return this == DECLINED || this == CANCELLED || this == COMPLETED;
    }
}
