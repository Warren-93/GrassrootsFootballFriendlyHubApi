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
        DeletionRequest doc = mongoTemplate.findById(id, DeletionRequest.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<DeletionRequest> findPendingFor(DeletionTargetType targetType, String targetId) {
        Query query = new Query(Criteria.where("targetType").is(targetType.name())
                .and("targetId").is(targetId)
                .and("status").is(DeletionRequestStatus.PENDING.name()));
        DeletionRequest doc = mongoTemplate.findOne(query, DeletionRequest.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<DeletionRequest> list(DeletionRequestStatus status, int limit) {
        Query query = status == null ? new Query() : new Query(Criteria.where("status").is(status.name()));
        query.with(Sort.by(Sort.Direction.DESC, "requestedAt")).limit(limit);
        return mongoTemplate.find(query, DeletionRequest.class, COLLECTION);
    }

    @Override
    public DeletionRequest save(DeletionRequest request) {
        DeletionRequest toSave = request.getId() != null ? request : withGeneratedId(request);
        DeletionRequest doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public Optional<DeletionRequest> saveIfStatus(DeletionRequest updated, DeletionRequestStatus expected) {
        Query query = new Query(Criteria.where("_id").is(updated.getId()).and("status").is(expected.name()));
        DeletionRequest replaced = mongoTemplate.findAndReplace(query, (updated),
                FindAndReplaceOptions.options().returnNew(), DeletionRequest.class, COLLECTION);
        return Optional.ofNullable(replaced);
    }

    private DeletionRequest withGeneratedId(DeletionRequest r) {
        return new DeletionRequest(UUID.randomUUID().toString(), r.getTargetType(), r.getTargetId(), r.getTargetLabel(),
                r.getReason(), r.getStatus(), r.getRequestedByAdminId(), r.getRequestedByEmail(),
                r.getRequestedAt() != null ? r.getRequestedAt() : Instant.now(), r.getDecidedByAdminId(),
                r.getDecidedByEmail(), r.getDecidedAt(), r.getDecisionNote(), r.getSummary(), r.getResult());
    }
}
