package com.gffh.api.repository.mongo;

import com.gffh.api.domain.PlatformAdmin;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("platform_admins")
public class PlatformAdminDocument {

    @Id
    public String id;
    public String email;
    public String passwordHash;
    public String totpSecret;
    public Instant createdAt;
    public Instant lastLoginAt;

    public static PlatformAdminDocument from(PlatformAdmin a) {
        PlatformAdminDocument d = new PlatformAdminDocument();
        d.id = a.id();
        d.email = a.email();
        d.passwordHash = a.passwordHash();
        d.totpSecret = a.totpSecret();
        d.createdAt = a.createdAt();
        d.lastLoginAt = a.lastLoginAt();
        return d;
    }

    public PlatformAdmin toDomain() {
        return new PlatformAdmin(id, email, passwordHash, totpSecret, createdAt, lastLoginAt);
    }
}
