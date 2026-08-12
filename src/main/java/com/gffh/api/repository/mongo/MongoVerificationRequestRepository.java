package com.gffh.api.repository.mongo;

import com.gffh.api.domain.VerificationRequest;
import com.gffh.api.domain.VerificationRequestStatus;
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
        VerificationRequestDocument doc = mongoTemplate.findById(id, VerificationRequestDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(VerificationRequestDocument::toDomain);
    }

    @Override
    public Optional<VerificationRequest> findPendingByTeamId(String teamId) {
        Query query = new Query(Criteria.where("teamId").is(teamId)
                .and("status").in(VerificationRequestStatus.PENDING.name(),
                        VerificationRequestStatus.AWAITING_SECOND_REJECTION.name()));
        VerificationRequestDocument doc = mongoTemplate.findOne(query, VerificationRequestDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(VerificationRequestDocument::toDomain);
    }

    @Override
    public Optional<VerificationRequest> findLatestByTeamId(String teamId) {
        Query query = new Query(Criteria.where("teamId").is(teamId));
        query.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "submittedAt"));
        query.limit(1);
        VerificationRequestDocument doc = mongoTemplate.findOne(query, VerificationRequestDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(VerificationRequestDocument::toDomain);
    }

    @Override
    public List<VerificationRequest> findByStatus(VerificationRequestStatus status) {
        Query query = new Query(Criteria.where("status").is(status.name()));
        return mongoTemplate.find(query, VerificationRequestDocument.class, COLLECTION).stream()
                .map(VerificationRequestDocument::toDomain).toList();
    }

    @Override
    public VerificationRequest save(VerificationRequest request) {
        VerificationRequest toSave = request.id() != null ? request : withGeneratedId(request);
        VerificationRequestDocument doc = VerificationRequestDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private VerificationRequest withGeneratedId(VerificationRequest r) {
        return new VerificationRequest(UUID.randomUUID().toString(), r.teamId(), r.submittedByUserId(),
                r.affiliationNumber(), r.contactDetails(), r.evidenceUrls(), r.status(),
                r.firstRejectionAdminId(), r.firstRejectionReason(), r.finalRejectionReason(),
                r.reviewedByAdminId(), r.submittedAt() != null ? r.submittedAt() : Instant.now(), r.reviewedAt());
    }
}
