package com.gffh.api.repository;

import com.gffh.api.model.NotificationPreference;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Keyed by user id: one document per user. */
public interface NotificationPreferenceRepository extends MongoRepository<NotificationPreference, String> {
}
