package com.gffh.api.repository.mongo;

import com.gffh.api.model.DeletionRequest;
import com.gffh.api.model.enums.DeletionRequestStatus;
import com.gffh.api.model.enums.DeletionTargetType;
import com.gffh.api.repository.DeletionRequestRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoDeletionRequestRepository implements DeletionRequestRepository {

    private static final String COLLECTION = "deletion_requests";

    private final MongoTemplate mongoTemplate;

    public MongoDeletionRequestRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<DeletionRequest> findById(String id) {
        DeletionRequestDocument doc = mongoTemplate.findById(id, DeletionRequestDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(DeletionRequestDocument::toDomain);
    }

    @Override
    public Optional<DeletionRequest> findPendingFor(DeletionTargetType targetType, String targetId) {
        Query query = new Query(Criteria.where("targetType").is(targetType.name())
                .and("targetId").is(targetId)
                .and("status").is(DeletionRequestStatus.PENDING.name()));
        DeletionRequestDocument doc = mongoTemplate.findOne(query, DeletionRequestDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(DeletionRequestDocument::toDomain);
    }

    @Override
    public List<DeletionRequest> list(DeletionRequestStatus status, int limit) {
        Query query = status == null ? new Query() : new Query(Criteria.where("status").is(status.name()));
        query.with(Sort.by(Sort.Direction.DESC, "requestedAt")).limit(limit);
        return mongoTemplate.find(query, DeletionRequestDocument.class, COLLECTION).stream()
                .map(DeletionRequestDocument::toDomain).toList();
    }

    @Override
    public DeletionRequest save(DeletionRequest request) {
        DeletionRequest toSave = request.getId() != null ? request : withGeneratedId(request);
        DeletionRequestDocument doc = DeletionRequestDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public Optional<DeletionRequest> saveIfStatus(DeletionRequest updated, DeletionRequestStatus expected) {
        Query query = new Query(Criteria.where("_id").is(updated.getId()).and("status").is(expected.name()));
        DeletionRequestDocument replaced = mongoTemplate.findAndReplace(query, DeletionRequestDocument.from(updated),
                FindAndReplaceOptions.options().returnNew(), DeletionRequestDocument.class, COLLECTION);
        return Optional.ofNullable(replaced).map(DeletionRequestDocument::toDomain);
    }

    private DeletionRequest withGeneratedId(DeletionRequest r) {
        return new DeletionRequest(UUID.randomUUID().toString(), r.getTargetType(), r.getTargetId(), r.getTargetLabel(),
                r.getReason(), r.getStatus(), r.getRequestedByAdminId(), r.getRequestedByEmail(),
                r.getRequestedAt() != null ? r.getRequestedAt() : Instant.now(), r.getDecidedByAdminId(),
                r.getDecidedByEmail(), r.getDecidedAt(), r.getDecisionNote(), r.getSummary(), r.getResult());
    }
}
