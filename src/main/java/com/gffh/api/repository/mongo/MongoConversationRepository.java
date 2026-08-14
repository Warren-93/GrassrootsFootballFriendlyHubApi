package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Conversation;
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
        ConversationDocument doc = mongoTemplate.findById(id, ConversationDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(ConversationDocument::toDomain);
    }

    @Override
    public Optional<Conversation> findBetweenTeams(String teamAId, String teamBId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("teamAId").is(teamAId).and("teamBId").is(teamBId),
                Criteria.where("teamAId").is(teamBId).and("teamBId").is(teamAId)));
        ConversationDocument doc = mongoTemplate.findOne(query, ConversationDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(ConversationDocument::toDomain);
    }

    @Override
    public List<Conversation> findByTeamId(String teamId) {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("teamAId").is(teamId),
                Criteria.where("teamBId").is(teamId)))
                .with(Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        return mongoTemplate.find(query, ConversationDocument.class, COLLECTION).stream()
                .map(ConversationDocument::toDomain).toList();
    }

    @Override
    public Conversation save(Conversation conversation) {
        Conversation toSave = conversation.id() != null ? conversation : withGeneratedId(conversation);
        ConversationDocument doc = ConversationDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private Conversation withGeneratedId(Conversation c) {
        Instant now = c.createdAt() != null ? c.createdAt() : Instant.now();
        return new Conversation(UUID.randomUUID().toString(), c.teamAId(), c.teamBId(), now,
                c.lastMessageAt() != null ? c.lastMessageAt() : now, c.lastMessageBody(), c.lastMessageSenderTeamId());
    }
}
