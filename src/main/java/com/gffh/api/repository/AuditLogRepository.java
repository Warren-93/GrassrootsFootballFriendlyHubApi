package com.gffh.api.repository;

import com.gffh.api.domain.AuditLogEntry;

import java.util.List;

public interface AuditLogRepository {

    AuditLogEntry save(AuditLogEntry entry);

    List<AuditLogEntry> list(String targetType, String targetId, int limit);
}
