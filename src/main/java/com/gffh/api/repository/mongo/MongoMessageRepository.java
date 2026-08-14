package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Message;
import com.gffh.api.repository.MessageRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MongoMessageRepository implements MessageRepository {

    private static final String COLLECTION = "messages";

    private final MongoTemplate mongoTemplate;

    public MongoMessageRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Message save(Message message) {
        Message toSave = message.id() != null ? message : withGeneratedId(message);
        MessageDocument doc = MessageDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        Query query = new Query(Criteria.where("conversationId").is(conversationId))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        return mongoTemplate.find(query, MessageDocument.class, COLLECTION).stream()
                .map(MessageDocument::toDomain).toList();
    }

    private Message withGeneratedId(Message m) {
        return new Message(UUID.randomUUID().toString(), m.conversationId(), m.senderTeamId(), m.senderUserId(),
                m.body(), m.createdAt() != null ? m.createdAt() : Instant.now());
    }
}
