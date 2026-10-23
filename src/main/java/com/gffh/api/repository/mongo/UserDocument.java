package com.gffh.api.repository.mongo;

import com.gffh.api.model.User;
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
        d.id = u.getId();
        d.email = u.getEmail();
        d.passwordHash = u.getPasswordHash();
        d.displayName = u.getDisplayName();
        d.emailVerified = u.isEmailVerified();
        d.createdAt = u.getCreatedAt();
        d.suspended = u.isSuspended();
        return d;
    }

    public User toDomain() {
        return new User(id, email, passwordHash, displayName, emailVerified, createdAt, suspended);
    }
}
