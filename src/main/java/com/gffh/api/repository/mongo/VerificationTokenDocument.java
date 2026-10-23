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
        d.id = t.getId();
        d.userId = t.getUserId();
        d.token = t.getToken();
        d.purpose = t.getPurpose().name();
        d.email = t.getEmail();
        d.expiresAt = t.getExpiresAt();
        d.used = t.isUsed();
        d.createdAt = t.getCreatedAt();
        return d;
    }

    public VerificationToken toDomain() {
        return new VerificationToken(id, userId, token, VerificationTokenPurpose.valueOf(purpose),
                email, expiresAt, used, createdAt);
    }
}
