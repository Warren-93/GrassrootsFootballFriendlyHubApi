package com.gffh.api.domain;

import java.time.Instant;

/**
 * An in-app notification for one user (SCR-HM-02). Fanned out at creation time
 * to every manager of the relevant team, rather than resolved per-viewer, so
 * read state is personal even when several people manage the same team.
 */
public record Notification(
        String id,
        String userId,
        NotificationType type,
        String title,
        String body,
        String relatedTeamId,
        String relatedRequestId,
        String relatedFixtureId,
        String relatedConversationId,
        boolean read,
        Instant createdAt) {
}
