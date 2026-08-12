package com.gffh.api.repository.mongo;

import com.gffh.api.domain.AuditLogEntry;
import com.gffh.api.repository.AuditLogRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MongoAuditLogRepository implements AuditLogRepository {

    private static final String COLLECTION = "audit_log";

    private final MongoTemplate mongoTemplate;

    public MongoAuditLogRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        AuditLogEntry toSave = entry.id() != null ? entry : withGeneratedId(entry);
        AuditLogDocument doc = AuditLogDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public List<AuditLogEntry> list(String targetType, String targetId, int limit) {
        Criteria criteria = new Criteria();
        if (targetType != null) criteria = criteria.and("targetType").is(targetType);
        if (targetId != null) criteria = criteria.and("targetId").is(targetId);
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(limit);
        return mongoTemplate.find(query, AuditLogDocument.class, COLLECTION).stream()
                .map(AuditLogDocument::toDomain).toList();
    }

    private AuditLogEntry withGeneratedId(AuditLogEntry entry) {
        return new AuditLogEntry(UUID.randomUUID().toString(), entry.actorAdminId(), entry.actorEmail(),
                entry.action(), entry.targetType(), entry.targetId(), entry.reason(),
                entry.timestamp() != null ? entry.timestamp() : Instant.now());
    }
}
