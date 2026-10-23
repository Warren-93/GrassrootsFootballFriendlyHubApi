package com.gffh.api.repository;

import com.gffh.api.model.AuditLogEntry;
import com.gffh.api.repository.custom.AuditLogRepositoryCustom;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLogEntry, String>, AuditLogRepositoryCustom {
}
