package com.gffh.api.domain;

import java.time.Instant;

/**
 * An immutable record of one administrator action (ADM-09). Written by
 * {@code AuditLogService}, never edited or deleted - there is deliberately no
 * update/delete path anywhere in this type's lifecycle.
 */
public record AuditLogEntry(
        String id,
        String actorAdminId,
        String actorEmail,
        String action,
        String targetType,
        String targetId,
        String reason,
        Instant timestamp) {
}
