package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Notification;
import com.gffh.api.domain.NotificationType;
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
    public boolean read;
    public Instant createdAt;

    public static NotificationDocument from(Notification n) {
        NotificationDocument d = new NotificationDocument();
        d.id = n.id();
        d.userId = n.userId();
        d.type = n.type().name();
        d.title = n.title();
        d.body = n.body();
        d.relatedTeamId = n.relatedTeamId();
        d.relatedRequestId = n.relatedRequestId();
        d.relatedFixtureId = n.relatedFixtureId();
        d.read = n.read();
        d.createdAt = n.createdAt();
        return d;
    }

    public Notification toDomain() {
        return new Notification(id, userId, NotificationType.valueOf(type), title, body,
                relatedTeamId, relatedRequestId, relatedFixtureId, read, createdAt);
    }
}
