package com.gffh.api.repository.mongo;

import com.gffh.api.domain.Notification;
import com.gffh.api.repository.NotificationRepository;
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
public class MongoNotificationRepository implements NotificationRepository {

    private static final String COLLECTION = "notifications";

    private final MongoTemplate mongoTemplate;

    public MongoNotificationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Notification save(Notification notification) {
        Notification toSave = notification.id() != null ? notification : withGeneratedId(notification);
        NotificationDocument doc = NotificationDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public Optional<Notification> findById(String id) {
        NotificationDocument doc = mongoTemplate.findById(id, NotificationDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(NotificationDocument::toDomain);
    }

    @Override
    public List<Notification> findByUserId(String userId, int limit) {
        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);
        return mongoTemplate.find(query, NotificationDocument.class, COLLECTION).stream()
                .map(NotificationDocument::toDomain).toList();
    }

    @Override
    public long countUnread(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId).and("read").is(false));
        return mongoTemplate.count(query, NotificationDocument.class, COLLECTION);
    }

    private Notification withGeneratedId(Notification n) {
        return new Notification(UUID.randomUUID().toString(), n.userId(), n.type(), n.title(), n.body(),
                n.relatedTeamId(), n.relatedRequestId(), n.relatedFixtureId(), n.relatedConversationId(), n.read(),
                n.createdAt() != null ? n.createdAt() : Instant.now());
    }
}
