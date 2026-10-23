package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A platform account. Team- and club-level permissions live in {@link Membership},
 * never here - a user's authority always depends on which resource is being acted
 * on (Technical Specification section 7).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private String id;
    private String email;
    private String passwordHash;
    private String displayName;
    private boolean emailVerified;
    private Instant createdAt;
    private boolean suspended;
}
