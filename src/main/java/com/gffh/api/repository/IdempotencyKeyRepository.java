package com.gffh.api.repository;

import java.util.Optional;

/**
 * Records {@code Idempotency-Key} headers against the resource they created,
 * so a retried request (network timeout, double-tap) replays the original
 * result instead of creating a duplicate. Entries expire after 24 hours -
 * see the TTL index in {@code IndexConfig} - since a retry long after that
 * window is a new attempt, not a resend.
 */
public interface IdempotencyKeyRepository {

    /** The resource id previously created for this key, if any. */
    Optional<String> resourceIdFor(String key);

    /**
     * Records the key. Returns false without overwriting anything if another
     * request already claimed this exact key first (a race between two
     * concurrent retries) - the caller should look the winner up instead.
     */
    boolean claim(String key, String resourceId);
}
