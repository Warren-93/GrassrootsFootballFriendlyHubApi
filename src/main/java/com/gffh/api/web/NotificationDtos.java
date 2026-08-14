package com.gffh.api.web;

import com.gffh.api.domain.Notification;

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

    public record PreferenceView(boolean friendlyRequests, boolean fixtures, boolean verification, boolean messages) {}

    public record UpdatePreferenceRequest(boolean friendlyRequests, boolean fixtures, boolean verification, boolean messages) {}
}
