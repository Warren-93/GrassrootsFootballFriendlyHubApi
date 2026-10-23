package com.gffh.api.repository.mongo;

import com.gffh.api.model.Conversation;
import com.gffh.api.repository.ConversationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoConversationRepository implements ConversationRepository {

    private static final String COLLECTION = "conversations";

    private final MongoTemplate mongoTemplate;

    public MongoConversationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Conversation> findById(String id) {
        Conversation doc = mongoTemplate.findById(id, Conversation.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public Optional<Conversation> findBetweenTeams(String teamAId, String teamBId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("teamAId").is(teamAId).and("teamBId").is(teamBId),
                Criteria.where("teamAId").is(teamBId).and("teamBId").is(teamAId)));
        Conversation doc = mongoTemplate.findOne(query, Conversation.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public List<Conversation> findByTeamId(String teamId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("teamAId").is(teamId),
                Criteria.where("teamBId").is(teamId)))
                .with(Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        return mongoTemplate.find(query, Conversation.class, COLLECTION);
    }

    @Override
    public Conversation save(Conversation conversation) {
        Conversation toSave = conversation.getId() != null ? conversation : withGeneratedId(conversation);
        Conversation doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    private Conversation withGeneratedId(Conversation c) {
        Instant now = c.getCreatedAt() != null ? c.getCreatedAt() : Instant.now();
        return new Conversation(UUID.randomUUID().toString(), c.getTeamAId(), c.getTeamBId(), now,
                c.getLastMessageAt() != null ? c.getLastMessageAt() : now, c.getLastMessageBody(), c.getLastMessageSenderTeamId(),
                c.getRelatedFixtureId());
    }
}
