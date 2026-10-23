package com.gffh.api.repository.mongo;

import com.gffh.api.model.NotificationPreference;
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
        NotificationPreference doc = mongoTemplate.findById(userId, NotificationPreference.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public NotificationPreference save(NotificationPreference preference) {
        NotificationPreference doc = preference;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }
}
