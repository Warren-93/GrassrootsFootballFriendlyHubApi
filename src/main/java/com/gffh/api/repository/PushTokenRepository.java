package com.gffh.api.repository;

import com.gffh.api.model.PushToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Keyed by the device token itself, so {@code save} inserts or replaces and a
 * device re-registering (or changing hands) never duplicates.
 * {@code deleteById} removes a token whoever holds it - for tokens FCM or APNs
 * report as no longer valid.
 */
public interface PushTokenRepository extends MongoRepository<PushToken, String> {

    List<PushToken> findByUserId(String userId);

    /** Removes the token only while it belongs to {@code userId}, so nobody can unregister someone else's device. */
    void deleteByTokenAndUserId(String token, String userId);

    void deleteByUserId(String userId);
}
