package com.gffh.api.domain;

import java.time.Instant;

/**
 * A platform account. Team- and club-level permissions live in {@link Membership},
 * never here - a user's authority always depends on which resource is being acted
 * on (Technical Specification section 7).
 */
public record User(
        String id,
        String email,
        String passwordHash,
        String displayName,
        boolean emailVerified,
        Instant createdAt,
        boolean suspended) {
}
