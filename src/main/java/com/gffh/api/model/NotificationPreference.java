package com.gffh.api.model;

import com.gffh.api.model.enums.NotificationCategory;

/**
 * SCR-PR-09. One row per user; created lazily with all categories on the first time it's read or written.
 *
 * <p>The four category flags decide which events reach the user at all. {@code emailEnabled} and
 * {@code pushEnabled} then decide which channels carry them beyond the in-app centre.
 */
public record NotificationPreference(
        String userId,
        boolean friendlyRequests,
        boolean fixtures,
        boolean verification,
        boolean messages,
        boolean emailEnabled,
        boolean pushEnabled) {

    public static NotificationPreference defaultsFor(String userId) {
        return new NotificationPreference(userId, true, true, true, true, true, true);
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
