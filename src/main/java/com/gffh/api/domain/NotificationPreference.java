package com.gffh.api.domain;

/** SCR-PR-09. One row per user; created lazily with all categories on the first time it's read or written. */
public record NotificationPreference(
        String userId,
        boolean friendlyRequests,
        boolean fixtures,
        boolean verification,
        boolean messages) {

    public static NotificationPreference defaultsFor(String userId) {
        return new NotificationPreference(userId, true, true, true, true);
    }

    public boolean allows(NotificationCategory category) {
        return switch (category) {
            case FRIENDLY_REQUESTS -> friendlyRequests;
            case FIXTURES -> fixtures;
            case VERIFICATION -> verification;
            case MESSAGES -> messages;
        };
    }
}
