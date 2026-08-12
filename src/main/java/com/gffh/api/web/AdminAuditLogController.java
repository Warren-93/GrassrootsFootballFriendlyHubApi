package com.gffh.api.web;

import com.gffh.api.domain.AuditLogEntry;
import com.gffh.api.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-log")
@Tag(name = "Admin audit log", description = "ADM-09: immutable, filterable log of every administrator action")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    public AdminAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogEntry>> list(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(auditLogService.list(targetType, targetId, limit));
    }
}
