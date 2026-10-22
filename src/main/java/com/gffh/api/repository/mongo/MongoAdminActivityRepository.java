package com.gffh.api.repository.mongo;

import com.gffh.api.model.Block;
import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.Message;
import com.gffh.api.model.RefreshToken;
import com.gffh.api.model.Report;
import com.gffh.api.model.VerificationRequest;
import com.gffh.api.model.VerificationToken;
import com.gffh.api.repository.AdminActivityRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MongoAdminActivityRepository implements AdminActivityRepository {

    private final MongoTemplate mongoTemplate;

    public MongoAdminActivityRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Map<String, Instant> lastActiveAt(Collection<String> userIds) {
        Map<String, Instant> latest = new HashMap<>();
        if (userIds.isEmpty()) return latest;
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").in(userIds)),
                Aggregation.group("userId").max("createdAt").as("latest"));
        for (Document row : mongoTemplate.aggregate(aggregation, "refreshTokens", Document.class)) {
            if (row.get("latest") instanceof Date d) latest.put(String.valueOf(row.get("_id")), d.toInstant());
        }
        return latest;
    }

    @Override
    public List<RefreshToken> sessionsFor(String userId, int limit) {
        return newest(Criteria.where("userId").is(userId), "createdAt", limit, RefreshTokenDocument.class, "refreshTokens")
                .stream().map(RefreshTokenDocument::toDomain).toList();
    }

    @Override
    public List<VerificationToken> emailLinksFor(String userId, int limit) {
        return newest(Criteria.where("userId").is(userId), "createdAt", limit, VerificationTokenDocument.class,
                "verificationTokens").stream().map(VerificationTokenDocument::toDomain).toList();
    }

    @Override
    public List<Message> messagesSentBy(String userId, int limit) {
        return newest(Criteria.where("senderUserId").is(userId), "createdAt", limit, MessageDocument.class, "messages")
                .stream().map(MessageDocument::toDomain).toList();
    }

    @Override
    public long messagesIn(Collection<String> conversationIds) {
        return mongoTemplate.count(new Query(Criteria.where("conversationId").in(conversationIds)), "messages");
    }

    @Override
    public List<String> requestIdsCreatedBy(String userId, int limit) {
        Query query = new Query(Criteria.where("createdByUserId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(limit);
        query.fields().include("_id");
        return mongoTemplate.find(query, Document.class, "friendlyRequests").stream()
                .map(d -> String.valueOf(d.get("_id"))).toList();
    }

    @Override
    public List<VerificationRequest> verificationRequestsSubmittedBy(String userId) {
        return newest(Criteria.where("submittedByUserId").is(userId), "submittedAt", 50,
                VerificationRequestDocument.class, "verification_requests")
                .stream().map(VerificationRequestDocument::toDomain).toList();
    }

    @Override
    public List<VerificationRequest> verificationRequestsFor(String teamId) {
        return newest(Criteria.where("teamId").is(teamId), "submittedAt", 50,
                VerificationRequestDocument.class, "verification_requests")
                .stream().map(VerificationRequestDocument::toDomain).toList();
    }

    @Override
    public List<MemberInvite> invitesSentBy(String userId, int limit) {
        return newest(Criteria.where("invitedByUserId").is(userId), "createdAt", limit, MemberInviteDocument.class,
                "memberInvites").stream().map(MemberInviteDocument::toDomain).toList();
    }

    @Override
    public List<Report> reportsInvolving(String teamId) {
        Criteria either = new Criteria().orOperator(
                Criteria.where("reporterTeamId").is(teamId), Criteria.where("reportedTeamId").is(teamId));
        return newest(either, "createdAt", 50, ReportDocument.class, "reports")
                .stream().map(ReportDocument::toDomain).toList();
    }

    @Override
    public List<Block> blocksInvolving(String teamId) {
        Criteria either = new Criteria().orOperator(
                Criteria.where("blockingTeamId").is(teamId), Criteria.where("blockedTeamId").is(teamId));
        return newest(either, "createdAt", 50, BlockDocument.class, "blocks")
                .stream().map(BlockDocument::toDomain).toList();
    }

    @Override
    public long notificationsFor(String userId) {
        return mongoTemplate.count(new Query(Criteria.where("userId").is(userId)), "notifications");
    }

    @Override
    public long availabilitySlotsFor(String teamId) {
        return mongoTemplate.count(new Query(Criteria.where("teamId").is(teamId)), "availability");
    }

    private <T> List<T> newest(Criteria criteria, String sortField, int limit, Class<T> type, String collection) {
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, sortField)).limit(limit);
        return mongoTemplate.find(query, type, collection);
    }
}
