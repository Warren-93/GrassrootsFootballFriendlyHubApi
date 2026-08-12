package com.gffh.api.repository.mongo;

import com.gffh.api.domain.NotificationPreference;
import com.gffh.api.repository.NotificationPreferenceRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MongoNotificationPreferenceRepository implements NotificationPreferenceRepository {

    private static final String COLLECTION = "notificationPreferences";

    private final MongoTemplate mongoTemplate;

    public MongoNotificationPreferenceRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<NotificationPreference> findByUserId(String userId) {
        NotificationPreferenceDocument doc = mongoTemplate.findById(userId, NotificationPreferenceDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(NotificationPreferenceDocument::toDomain);
    }

    @Override
    public NotificationPreference save(NotificationPreference preference) {
        NotificationPreferenceDocument doc = NotificationPreferenceDocument.from(preference);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }
}
