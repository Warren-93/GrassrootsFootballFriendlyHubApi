package com.gffh.api.repository.custom;

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
        return newest(Criteria.where("userId").is(userId), "createdAt", limit, RefreshToken.class, "refreshTokens")
                ;
    }

    @Override
    public List<VerificationToken> emailLinksFor(String userId, int limit) {
        return newest(Criteria.where("userId").is(userId), "createdAt", limit, VerificationToken.class,
                "verificationTokens");
    }

    @Override
    public List<Message> messagesSentBy(String userId, int limit) {
        return newest(Criteria.where("senderUserId").is(userId), "createdAt", limit, Message.class, "messages")
                ;
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
                VerificationRequest.class, "verification_requests")
                ;
    }

    @Override
    public List<VerificationRequest> verificationRequestsFor(String teamId) {
        return newest(Criteria.where("teamId").is(teamId), "submittedAt", 50,
                VerificationRequest.class, "verification_requests")
                ;
    }

    @Override
    public List<MemberInvite> invitesSentBy(String userId, int limit) {
        return newest(Criteria.where("invitedByUserId").is(userId), "createdAt", limit, MemberInvite.class,
                "memberInvites");
    }

    @Override
    public List<Report> reportsInvolving(String teamId) {
        Criteria either = new Criteria().orOperator(
                Criteria.where("reporterTeamId").is(teamId), Criteria.where("reportedTeamId").is(teamId));
        return newest(either, "createdAt", 50, Report.class, "reports")
                ;
    }

    @Override
    public List<Block> blocksInvolving(String teamId) {
        Criteria either = new Criteria().orOperator(
                Criteria.where("blockingTeamId").is(teamId), Criteria.where("blockedTeamId").is(teamId));
        return newest(either, "createdAt", 50, Block.class, "blocks")
                ;
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
