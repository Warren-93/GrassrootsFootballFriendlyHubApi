package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * An immutable record of one administrator action (ADM-09). Written by
 * {@code AuditLogService}, never edited or deleted - there is deliberately no
 * update/delete path anywhere in this type's lifecycle.
 */
@Document("audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {

    @Id
    private String id;
    private String actorAdminId;
    private String actorEmail;
    private String action;
    private String targetType;
    private String targetId;
    private String reason;
    @CreatedDate
    private Instant timestamp;
}
