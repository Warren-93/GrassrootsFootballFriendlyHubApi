package com.gffh.api.repository.mongo;

import com.gffh.api.domain.VerificationToken;
import com.gffh.api.domain.VerificationTokenPurpose;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("verificationTokens")
public class VerificationTokenDocument {

    @Id
    public String id;
    public String userId;
    public String token;
    public String purpose;
    public Instant expiresAt;
    public boolean used;
    public Instant createdAt;

    public static VerificationTokenDocument from(VerificationToken t) {
        VerificationTokenDocument d = new VerificationTokenDocument();
        d.id = t.id();
        d.userId = t.userId();
        d.token = t.token();
        d.purpose = t.purpose().name();
        d.expiresAt = t.expiresAt();
        d.used = t.used();
        d.createdAt = t.createdAt();
        return d;
    }

    public VerificationToken toDomain() {
        return new VerificationToken(id, userId, token, VerificationTokenPurpose.valueOf(purpose),
                expiresAt, used, createdAt);
    }
}
