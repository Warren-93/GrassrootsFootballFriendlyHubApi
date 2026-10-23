package com.gffh.api.model;

import com.gffh.api.model.enums.NotificationCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * SCR-PR-09. One row per user; created lazily with all categories on the first time it's read or written.
 *
 * <p>The four category flags decide which events reach the user at all. {@code emailEnabled} and
 * {@code pushEnabled} then decide which channels carry them beyond the in-app centre.
 */
@Document("notificationPreferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    private String userId;
    private boolean friendlyRequests;
    private boolean fixtures;
    private boolean verification;
    private boolean messages;
    /** Defaults to true so preferences stored before the channel switches existed keep both channels on. */
    private boolean emailEnabled = true;
    private boolean pushEnabled = true;

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
