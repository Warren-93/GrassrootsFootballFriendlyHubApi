package com.gffh.api.repository.mongo;

import com.gffh.api.domain.FriendlyRequest;
import com.gffh.api.domain.RequestStatus;
import com.gffh.api.repository.FriendlyRequestRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public class MongoFriendlyRequestRepository implements FriendlyRequestRepository {

    private static final String COLLECTION = "friendlyRequests";

    private static final List<String> TERMINAL = Stream.of(RequestStatus.values())
            .filter(RequestStatus::isTerminal).map(Enum::name).toList();

    private final MongoTemplate mongoTemplate;

    public MongoFriendlyRequestRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<FriendlyRequest> findById(String id) {
        FriendlyRequestDocument doc = mongoTemplate.findById(id, FriendlyRequestDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(FriendlyRequestDocument::toDomain);
    }

    @Override
    public List<FriendlyRequest> findByTeamId(String teamId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("senderTeamId").is(teamId),
                Criteria.where("recipientTeamId").is(teamId)));
        return mongoTemplate.find(query, FriendlyRequestDocument.class, COLLECTION).stream()
                .map(FriendlyRequestDocument::toDomain).toList();
    }

    @Override
    public boolean existsOpenBetween(String teamAId, String teamBId) {
        Criteria pair = new Criteria().orOperator(
                Criteria.where("senderTeamId").is(teamAId).and("recipientTeamId").is(teamBId),
                Criteria.where("senderTeamId").is(teamBId).and("recipientTeamId").is(teamAId));
        Query query = new Query(new Criteria().andOperator(pair, Criteria.where("status").nin(TERMINAL)));
        return mongoTemplate.exists(query, FriendlyRequestDocument.class, COLLECTION);
    }

    @Override
    public FriendlyRequest save(FriendlyRequest request) {
        FriendlyRequest toSave = request.id() != null ? request : withGeneratedId(request);
        FriendlyRequestDocument doc = FriendlyRequestDocument.from(toSave);
        doc.updatedAt = Instant.now();
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public void cancelOpenForTeam(String teamId) {
        Criteria pair = new Criteria().orOperator(
                Criteria.where("senderTeamId").is(teamId),
                Criteria.where("recipientTeamId").is(teamId));
        Query query = new Query(new Criteria().andOperator(pair, Criteria.where("status").nin(TERMINAL)));
        mongoTemplate.updateMulti(query,
                org.springframework.data.mongodb.core.query.Update.update("status", RequestStatus.CANCELLED.name())
                        .set("updatedAt", Instant.now()),
                FriendlyRequestDocument.class, COLLECTION);
    }

    private FriendlyRequest withGeneratedId(FriendlyRequest r) {
        Instant now = Instant.now();
        return new FriendlyRequest(UUID.randomUUID().toString(), r.senderTeamId(), r.recipientTeamId(),
                r.senderSlotId(), r.recipientSlotId(), r.status(), r.date(), r.startTime(), r.endTime(),
                r.venueId(), r.homeTeamId(), r.costShare(), r.refereeArrangement(), r.message(),
                r.createdByUserId(), now, now);
    }
}
