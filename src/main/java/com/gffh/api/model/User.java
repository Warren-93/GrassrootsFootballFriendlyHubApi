package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A platform account. Team- and club-level permissions live in {@link Membership},
 * never here - a user's authority always depends on which resource is being acted
 * on (Technical Specification section 7).
 */
@Document("users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;
    private String email;
    private String passwordHash;
    private String displayName;
    private boolean emailVerified;
    private Instant createdAt;
    private boolean suspended;
}
