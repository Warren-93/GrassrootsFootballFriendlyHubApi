package com.gffh.api.repository.mongo;

import com.gffh.api.model.VerificationRequest;
import com.gffh.api.model.enums.VerificationRequestStatus;
import com.gffh.api.repository.VerificationRequestRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoVerificationRequestRepository implements VerificationRequestRepository {

    private static final String COLLECTION = "verification_requests";

    private final MongoTemplate mongoTemplate;

    public MongoVerificationRequestRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<VerificationRequest> findById(String id) {
        VerificationRequest doc = mongoTemplate.findById(id, VerificationRequest.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<VerificationRequest> findPendingByTeamId(String teamId) {
        Query query = new Query(Criteria.where("teamId").is(teamId)
                .and("status").in(VerificationRequestStatus.PENDING.name(),
                        VerificationRequestStatus.AWAITING_SECOND_REJECTION.name()));
        VerificationRequest doc = mongoTemplate.findOne(query, VerificationRequest.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<VerificationRequest> findLatestByTeamId(String teamId) {
        Query query = new Query(Criteria.where("teamId").is(teamId));
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "submittedAt"));
        query.limit(1);
        VerificationRequest doc = mongoTemplate.findOne(query, VerificationRequest.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<VerificationRequest> findByStatus(VerificationRequestStatus status) {
        Query query = new Query(Criteria.where("status").is(status.name()));
        return mongoTemplate.find(query, VerificationRequest.class, COLLECTION);
    }

    @Override
    public VerificationRequest save(VerificationRequest request) {
        VerificationRequest toSave = request.getId() != null ? request : withGeneratedId(request);
        VerificationRequest doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    private VerificationRequest withGeneratedId(VerificationRequest r) {
        return new VerificationRequest(UUID.randomUUID().toString(), r.getTeamId(), r.getSubmittedByUserId(),
                r.getAffiliationNumber(), r.getContactDetails(), r.getEvidenceUrls(), r.getStatus(),
                r.getFirstRejectionAdminId(), r.getFirstRejectionReason(), r.getFinalRejectionReason(),
                r.getReviewedByAdminId(), r.getSubmittedAt() != null ? r.getSubmittedAt() : Instant.now(), r.getReviewedAt());
    }
}
