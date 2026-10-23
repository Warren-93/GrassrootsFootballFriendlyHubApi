package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An immutable record of one administrator action (ADM-09). Written by
 * {@code AuditLogService}, never edited or deleted - there is deliberately no
 * update/delete path anywhere in this type's lifecycle.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntry {

    private String id;
    private String actorAdminId;
    private String actorEmail;
    private String action;
    private String targetType;
    private String targetId;
    private String reason;
    private Instant timestamp;
}
