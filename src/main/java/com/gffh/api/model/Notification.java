package com.gffh.api.model;

import com.gffh.api.model.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An in-app notification for one user (SCR-HM-02). Fanned out at creation time
 * to every manager of the relevant team, rather than resolved per-viewer, so
 * read state is personal even when several people manage the same team.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private String id;
    private String userId;
    private NotificationType type;
    private String title;
    private String body;
    private String relatedTeamId;
    private String relatedRequestId;
    private String relatedFixtureId;
    private String relatedConversationId;
    private boolean read;
    private Instant createdAt;
}
