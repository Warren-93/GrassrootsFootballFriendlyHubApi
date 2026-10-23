package com.gffh.api.repository.custom;

import com.gffh.api.model.AuditLogEntry;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

public class AuditLogRepositoryCustomImpl implements AuditLogRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public AuditLogRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<AuditLogEntry> list(String targetType, String targetId, int limit) {
        Criteria criteria = new Criteria();
        if (targetType != null) criteria = criteria.and("targetType").is(targetType);
        if (targetId != null) criteria = criteria.and("targetId").is(targetId);
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(limit);
        return mongoTemplate.find(query, AuditLogEntry.class);
    }
}
