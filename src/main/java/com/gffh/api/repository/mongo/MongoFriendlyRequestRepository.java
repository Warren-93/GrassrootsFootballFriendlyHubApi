package com.gffh.api.repository.mongo;

import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.enums.RequestStatus;
import com.gffh.api.repository.FriendlyRequestRepository;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public class MongoFriendlyRequestRepository implements FriendlyRequestRepository {

    private static final String COLLECTION = "friendlyRequests";

    private static final List<String> TERMINAL = Stream.of(RequestStatus.values())
            .filter(RequestStatus::isTerminal).map(Enum::name).toList();

    /** Still being negotiated - see {@link #existsOpenBetween}. ACCEPTED is transient but counts. */
    private static final List<String> NEGOTIATING = Stream.of(
            RequestStatus.SENT, RequestStatus.CHANGES_REQUESTED, RequestStatus.UPDATED, RequestStatus.ACCEPTED)
            .map(Enum::name).toList();

    private final MongoTemplate mongoTemplate;

    public MongoFriendlyRequestRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<FriendlyRequest> findById(String id) {
        FriendlyRequest doc = mongoTemplate.findById(id, FriendlyRequest.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<FriendlyRequest> findByTeamId(String teamId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("senderTeamId").is(teamId),
                Criteria.where("recipientTeamId").is(teamId)));
        return mongoTemplate.find(query, FriendlyRequest.class, COLLECTION);
    }

    @Override
    public List<FriendlyRequest> findConfirmedBefore(LocalDate date) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("status").is(RequestStatus.CONFIRMED.name()),
                Criteria.where("date").lt(date)));
        return mongoTemplate.find(query, FriendlyRequest.class, COLLECTION);
    }

    @Override
    public List<FriendlyRequest> findNegotiatingBefore(LocalDate date) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("status").in(NEGOTIATING),
                Criteria.where("date").lt(date)));
        return mongoTemplate.find(query, FriendlyRequest.class, COLLECTION);
    }

    @Override
    public boolean existsOpenBetween(String teamAId, String teamBId) {
        Criteria pair = new Criteria().orOperator(
                Criteria.where("senderTeamId").is(teamAId).and("recipientTeamId").is(teamBId),
                Criteria.where("senderTeamId").is(teamBId).and("recipientTeamId").is(teamAId));
        Query query = new Query(new Criteria().andOperator(pair, Criteria.where("status").in(NEGOTIATING)));
        return mongoTemplate.exists(query, FriendlyRequest.class, COLLECTION);
    }

    @Override
    public boolean existsBetween(String teamAId, String teamBId) {
        Criteria pair = new Criteria().orOperator(
                Criteria.where("senderTeamId").is(teamAId).and("recipientTeamId").is(teamBId),
                Criteria.where("senderTeamId").is(teamBId).and("recipientTeamId").is(teamAId));
        return mongoTemplate.exists(new Query(pair), FriendlyRequest.class, COLLECTION);
    }

    @Override
    public boolean existsOpenForTeam(String teamId) {
        Criteria team = new Criteria().orOperator(
                Criteria.where("senderTeamId").is(teamId),
                Criteria.where("recipientTeamId").is(teamId));
        Query query = new Query(new Criteria().andOperator(team, Criteria.where("status").nin(TERMINAL)));
        return mongoTemplate.exists(query, FriendlyRequest.class, COLLECTION);
    }

    @Override
    public FriendlyRequest save(FriendlyRequest request) {
        FriendlyRequest toSave = request.getId() != null ? request : withGeneratedId(request);
        FriendlyRequest doc = toSave;
        doc.setUpdatedAt(Instant.now());
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public Optional<FriendlyRequest> saveIfStatus(FriendlyRequest request, RequestStatus expectedStatus) {
        FriendlyRequest doc = request;
        doc.setUpdatedAt(Instant.now());
        Query query = new Query(Criteria.where("_id").is(request.getId()).and("status").is(expectedStatus.name()));
        FriendlyRequest saved = mongoTemplate.findAndReplace(query, doc,
                FindAndReplaceOptions.options().returnNew(), COLLECTION);
        return Optional.ofNullable(saved);
    }

    private FriendlyRequest withGeneratedId(FriendlyRequest r) {
        Instant now = Instant.now();
        return new FriendlyRequest(UUID.randomUUID().toString(), r.getSenderTeamId(), r.getRecipientTeamId(),
                r.getSenderSlotId(), r.getRecipientSlotId(), r.getStatus(), r.getDate(), r.getStartTime(), r.getEndTime(),
                r.getVenueId(), r.getHomeTeamId(), r.getCostShare(), r.getRefereeArrangement(), r.getMessage(),
                r.getCreatedByUserId(), now, now, r.getActionReason(),
                r.getProposedStartTime(), r.getProposedEndTime(), r.getProposedVenueId());
    }
}
