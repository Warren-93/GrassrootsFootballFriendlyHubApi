package com.gffh.api.repository.mongo;

import com.gffh.api.model.Notification;
import com.gffh.api.model.enums.NotificationType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("notifications")
public class NotificationDocument {

    @Id
    public String id;
    public String userId;
    public String type;
    public String title;
    public String body;
    public String relatedTeamId;
    public String relatedRequestId;
    public String relatedFixtureId;
    public String relatedConversationId;
    public boolean read;
    public Instant createdAt;

    public static NotificationDocument from(Notification n) {
        NotificationDocument d = new NotificationDocument();
        d.id = n.getId();
        d.userId = n.getUserId();
        d.type = n.getType().name();
        d.title = n.getTitle();
        d.body = n.getBody();
        d.relatedTeamId = n.getRelatedTeamId();
        d.relatedRequestId = n.getRelatedRequestId();
        d.relatedFixtureId = n.getRelatedFixtureId();
        d.relatedConversationId = n.getRelatedConversationId();
        d.read = n.isRead();
        d.createdAt = n.getCreatedAt();
        return d;
    }

    public Notification toDomain() {
        return new Notification(id, userId, NotificationType.valueOf(type), title, body,
                relatedTeamId, relatedRequestId, relatedFixtureId, relatedConversationId, read, createdAt);
    }
}
