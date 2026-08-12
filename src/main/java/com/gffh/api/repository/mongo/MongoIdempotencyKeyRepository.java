package com.gffh.api.repository.mongo;

import com.gffh.api.repository.IdempotencyKeyRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class MongoIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private static final String COLLECTION = "idempotencyKeys";

    private final MongoTemplate mongoTemplate;

    public MongoIdempotencyKeyRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<String> resourceIdFor(String key) {
        IdempotencyKeyDocument doc = mongoTemplate.findById(key, IdempotencyKeyDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(d -> d.resourceId);
    }

    @Override
    public boolean claim(String key, String resourceId) {
        IdempotencyKeyDocument doc = new IdempotencyKeyDocument();
        doc.id = key;
        doc.resourceId = resourceId;
        doc.createdAt = Instant.now();
        try {
            mongoTemplate.insert(doc, COLLECTION);
            return true;
        } catch (DuplicateKeyException e) {
            // Lost the race to a concurrent retry with the same key - its
            // result is the one that counts, not this one.
            return false;
        }
    }
}
