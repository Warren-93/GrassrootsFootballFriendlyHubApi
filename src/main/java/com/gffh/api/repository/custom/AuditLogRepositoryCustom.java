package com.gffh.api.repository.custom;

import com.gffh.api.model.AuditLogEntry;

import java.util.List;

/** Audit log queries that a derived query method can't express. */
public interface AuditLogRepositoryCustom {

    /** Newest first; {@code targetType} and {@code targetId} narrow the list only when given. */
    List<AuditLogEntry> list(String targetType, String targetId, int limit);
}
