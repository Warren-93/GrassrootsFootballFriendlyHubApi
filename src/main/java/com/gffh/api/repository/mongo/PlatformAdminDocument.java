package com.gffh.api.repository.mongo;

import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.model.enums.AdminRole;
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
    /** Null on accounts saved before admin roles existed. */
    public String role;

    public static PlatformAdminDocument from(PlatformAdmin a) {
        PlatformAdminDocument d = new PlatformAdminDocument();
        d.id = a.id();
        d.email = a.email();
        d.passwordHash = a.passwordHash();
        d.totpSecret = a.totpSecret();
        d.createdAt = a.createdAt();
        d.lastLoginAt = a.lastLoginAt();
        d.role = a.role() == null ? null : a.role().name();
        return d;
    }

    public PlatformAdmin toDomain() {
        return new PlatformAdmin(id, email, passwordHash, totpSecret, createdAt, lastLoginAt,
                role == null ? null : AdminRole.valueOf(role));
    }
}
