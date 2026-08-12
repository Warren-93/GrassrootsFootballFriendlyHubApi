package com.gffh.api.repository.mongo;

import com.gffh.api.domain.User;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("users")
public class UserDocument {

    @Id
    public String id;
    public String email;
    public String passwordHash;
    public String displayName;
    public boolean emailVerified;
    public Instant createdAt;
    public boolean suspended;

    public static UserDocument from(User u) {
        UserDocument d = new UserDocument();
        d.id = u.id();
        d.email = u.email();
        d.passwordHash = u.passwordHash();
        d.displayName = u.displayName();
        d.emailVerified = u.emailVerified();
        d.createdAt = u.createdAt();
        d.suspended = u.suspended();
        return d;
    }

    public User toDomain() {
        return new User(id, email, passwordHash, displayName, emailVerified, createdAt, suspended);
    }
}
