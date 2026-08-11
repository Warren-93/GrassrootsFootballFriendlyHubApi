package com.gffh.api.repository.mongo;

import com.gffh.api.domain.VerificationToken;
import com.gffh.api.domain.VerificationTokenPurpose;
import com.gffh.api.repository.VerificationTokenRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoVerificationTokenRepository implements VerificationTokenRepository {

    private static final String COLLECTION = "verificationTokens";

    private final MongoTemplate mongoTemplate;

    public MongoVerificationTokenRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<VerificationToken> findByToken(String token, VerificationTokenPurpose purpose) {
        Query query = new Query(Criteria.where("token").is(token).and("purpose").is(purpose.name()));
        VerificationTokenDocument doc = mongoTemplate.findOne(query, VerificationTokenDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(VerificationTokenDocument::toDomain);
    }

    @Override
    public VerificationToken save(VerificationToken token) {
        VerificationToken toSave = token.id() != null ? token : withGeneratedId(token);
        VerificationTokenDocument doc = VerificationTokenDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public void markUsed(String id) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(id)),
                Update.update("used", true), VerificationTokenDocument.class, COLLECTION);
    }

    private VerificationToken withGeneratedId(VerificationToken t) {
        return new VerificationToken(UUID.randomUUID().toString(), t.userId(), t.token(), t.purpose(),
                t.expiresAt(), t.used(), t.createdAt() != null ? t.createdAt() : Instant.now());
    }
}
