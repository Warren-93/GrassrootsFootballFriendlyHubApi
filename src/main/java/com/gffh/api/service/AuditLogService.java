package com.gffh.api.service;

import com.gffh.api.domain.AuditLogEntry;
import com.gffh.api.domain.PlatformAdmin;
import com.gffh.api.repository.AuditLogRepository;
import com.gffh.api.repository.PlatformAdminRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Records every administrator write action (ADM-09). Called from every admin
 * service that mutates something, never bypassed - there is no admin write
 * path that doesn't go through this.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLog;
    private final PlatformAdminRepository admins;

    public AuditLogService(AuditLogRepository auditLog, PlatformAdminRepository admins) {
        this.auditLog = auditLog;
        this.admins = admins;
    }

    public void record(String actorAdminId, String action, String targetType, String targetId, String reason) {
        String actorEmail = admins.findById(actorAdminId).map(PlatformAdmin::email).orElse("unknown");
        auditLog.save(new AuditLogEntry(null, actorAdminId, actorEmail, action, targetType, targetId, reason, Instant.now()));
    }

    public List<AuditLogEntry> list(String targetType, String targetId, int limit) {
        return auditLog.list(targetType, targetId, limit);
    }
}
