package com.gffh.api.repository.mongo;

import com.gffh.api.domain.RefreshToken;
import com.gffh.api.repository.RefreshTokenRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MongoRefreshTokenRepository implements RefreshTokenRepository {

    private static final String COLLECTION = "refreshTokens";

    private final MongoTemplate mongoTemplate;

    public MongoRefreshTokenRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        Query query = new Query(Criteria.where("tokenHash").is(tokenHash));
        RefreshTokenDocument doc = mongoTemplate.findOne(query, RefreshTokenDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(RefreshTokenDocument::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshToken toSave = token.id() != null ? token : withGeneratedId(token);
        RefreshTokenDocument doc = RefreshTokenDocument.from(toSave);
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    @Override
    public void revoke(String id) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(id)),
                Update.update("revoked", true), RefreshTokenDocument.class, COLLECTION);
    }

    @Override
    public void revokeAllForUser(String userId) {
        mongoTemplate.updateMulti(new Query(Criteria.where("userId").is(userId)),
                Update.update("revoked", true), RefreshTokenDocument.class, COLLECTION);
    }

    private RefreshToken withGeneratedId(RefreshToken t) {
        return new RefreshToken(UUID.randomUUID().toString(), t.userId(), t.tokenHash(),
                t.expiresAt(), t.revoked(), t.createdAt() != null ? t.createdAt() : Instant.now());
    }
}
