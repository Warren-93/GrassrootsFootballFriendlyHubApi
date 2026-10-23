package com.gffh.api.repository.mongo;

import com.gffh.api.model.RefreshToken;
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
        RefreshToken doc = mongoTemplate.findOne(query, RefreshToken.class, COLLECTION);
        return Optional.ofNullable(doc);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshToken toSave = token.getId() != null ? token : withGeneratedId(token);
        RefreshToken doc = toSave;
        mongoTemplate.save(doc, COLLECTION);
        return doc;
    }

    @Override
    public void revoke(String id) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(id)),
                Update.update("revoked", true).unset("rotatedAt"), RefreshToken.class, COLLECTION);
    }

    @Override
    public void markRotated(String id, Instant at) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(id)),
                Update.update("revoked", true).set("rotatedAt", at), RefreshToken.class, COLLECTION);
    }

    @Override
    public void revokeAllForUser(String userId) {
        mongoTemplate.updateMulti(new Query(Criteria.where("userId").is(userId)),
                Update.update("revoked", true).unset("rotatedAt"), RefreshToken.class, COLLECTION);
    }

    @Override
    public void revokeAllForUserExcept(String userId, String keepTokenId) {
        mongoTemplate.updateMulti(new Query(Criteria.where("userId").is(userId).and("_id").ne(keepTokenId)),
                Update.update("revoked", true).unset("rotatedAt"), RefreshToken.class, COLLECTION);
    }

    private RefreshToken withGeneratedId(RefreshToken t) {
        return new RefreshToken(UUID.randomUUID().toString(), t.getUserId(), t.getTokenHash(),
                t.getExpiresAt(), t.isRevoked(), t.getCreatedAt() != null ? t.getCreatedAt() : Instant.now(), t.getRotatedAt());
    }
}
