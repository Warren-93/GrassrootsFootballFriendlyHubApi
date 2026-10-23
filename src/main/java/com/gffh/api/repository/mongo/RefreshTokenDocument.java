package com.gffh.api.repository.mongo;

import com.gffh.api.model.RefreshToken;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("refreshTokens")
public class RefreshTokenDocument {

    @Id
    public String id;
    public String userId;
    public String tokenHash;
    public Instant expiresAt;
    public boolean revoked;
    public Instant createdAt;
    public Instant rotatedAt;

    public static RefreshTokenDocument from(RefreshToken t) {
        RefreshTokenDocument d = new RefreshTokenDocument();
        d.id = t.getId();
        d.userId = t.getUserId();
        d.tokenHash = t.getTokenHash();
        d.expiresAt = t.getExpiresAt();
        d.revoked = t.isRevoked();
        d.createdAt = t.getCreatedAt();
        d.rotatedAt = t.getRotatedAt();
        return d;
    }

    public RefreshToken toDomain() {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revoked, createdAt, rotatedAt);
    }
}
