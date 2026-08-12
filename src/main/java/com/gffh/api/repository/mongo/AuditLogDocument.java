package com.gffh.api.repository.mongo;

import com.gffh.api.domain.AuditLogEntry;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("audit_log")
public class AuditLogDocument {

    @Id
    public String id;
    public String actorAdminId;
    public String actorEmail;
    public String action;
    public String targetType;
    public String targetId;
    public String reason;
    public Instant timestamp;

    public static AuditLogDocument from(AuditLogEntry e) {
        AuditLogDocument d = new AuditLogDocument();
        d.id = e.id();
        d.actorAdminId = e.actorAdminId();
        d.actorEmail = e.actorEmail();
        d.action = e.action();
        d.targetType = e.targetType();
        d.targetId = e.targetId();
        d.reason = e.reason();
        d.timestamp = e.timestamp();
        return d;
    }

    public AuditLogEntry toDomain() {
        return new AuditLogEntry(id, actorAdminId, actorEmail, action, targetType, targetId, reason, timestamp);
    }
}
