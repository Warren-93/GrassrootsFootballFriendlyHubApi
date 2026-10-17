package com.gffh.api.repository.mongo;

import com.gffh.api.model.VerificationToken;
import com.gffh.api.model.enums.VerificationTokenPurpose;
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
    /** Null on tokens saved before the address was recorded. */
    public String email;
    public Instant expiresAt;
    public boolean used;
    public Instant createdAt;

    public static VerificationTokenDocument from(VerificationToken t) {
        VerificationTokenDocument d = new VerificationTokenDocument();
        d.id = t.id();
        d.userId = t.userId();
        d.token = t.token();
        d.purpose = t.purpose().name();
        d.email = t.email();
        d.expiresAt = t.expiresAt();
        d.used = t.used();
        d.createdAt = t.createdAt();
        return d;
    }

    public VerificationToken toDomain() {
        return new VerificationToken(id, userId, token, VerificationTokenPurpose.valueOf(purpose),
                email, expiresAt, used, createdAt);
    }
}
