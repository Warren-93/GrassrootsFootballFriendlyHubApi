package com.gffh.api.repository;

import com.gffh.api.model.IdempotencyKey;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Records {@code Idempotency-Key} headers against the resource they created,
 * so a retried request (network timeout, double-tap) replays the original
 * result instead of creating a duplicate. Entries expire after 24 hours -
 * see the TTL index in {@code IndexConfig} - since a retry long after that
 * window is a new attempt, not a resend.
 */
public interface IdempotencyKeyRepository extends MongoRepository<IdempotencyKey, String> {

    /** The resource id previously created for this key, if any. */
    default Optional<String> resourceIdFor(String key) {
        return findById(key).map(IdempotencyKey::getResourceId);
    }

    /**
     * Records the key. Returns false without overwriting anything if another
     * request already claimed this exact key first (a race between two
     * concurrent retries) - the caller should look the winner up instead.
     */
    default boolean claim(String key, String resourceId) {
        try {
            insert(new IdempotencyKey(key, resourceId, Instant.now()));
            return true;
        } catch (DuplicateKeyException e) {
            // Lost the race to a concurrent retry with the same key - its
            // result is the one that counts, not this one.
            return false;
        }
    }
}
