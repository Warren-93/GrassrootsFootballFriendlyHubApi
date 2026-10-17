package com.gffh.api.dto;

import com.gffh.api.model.Notification;

import java.time.Instant;

/** Wire types for SCR-HM-02 Notification centre. */
public final class NotificationDtos {

    private NotificationDtos() {}

    public record NotificationView(String id, String type, String title, String body, String relatedTeamId,
                                    String relatedRequestId, String relatedFixtureId, String relatedConversationId,
                                    boolean read, Instant createdAt) {

        public static NotificationView from(Notification n) {
            return new NotificationView(n.id(), n.type().name(), n.title(), n.body(), n.relatedTeamId(),
                    n.relatedRequestId(), n.relatedFixtureId(), n.relatedConversationId(), n.read(), n.createdAt());
        }
    }

    public record UnreadCountView(long count) {}

    /** The four category toggles, then whether those notifications also go out by email and by push. */
    public record PreferenceView(boolean friendlyRequests, boolean fixtures, boolean verification, boolean messages,
                                 boolean emailEnabled, boolean pushEnabled) {}

    /** Every field is optional: null keeps the current setting, so a partial body switches nothing else off. */
    public record UpdatePreferenceRequest(Boolean friendlyRequests, Boolean fixtures, Boolean verification, Boolean messages,
                                          Boolean emailEnabled, Boolean pushEnabled) {}
}
