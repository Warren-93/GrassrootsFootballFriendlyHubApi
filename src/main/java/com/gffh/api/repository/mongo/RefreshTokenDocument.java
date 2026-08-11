package com.gffh.api.repository.mongo;

import com.gffh.api.domain.RefreshToken;
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

    public static RefreshTokenDocument from(RefreshToken t) {
        RefreshTokenDocument d = new RefreshTokenDocument();
        d.id = t.id();
        d.userId = t.userId();
        d.tokenHash = t.tokenHash();
        d.expiresAt = t.expiresAt();
        d.revoked = t.revoked();
        d.createdAt = t.createdAt();
        return d;
    }

    public RefreshToken toDomain() {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revoked, createdAt);
    }
}
