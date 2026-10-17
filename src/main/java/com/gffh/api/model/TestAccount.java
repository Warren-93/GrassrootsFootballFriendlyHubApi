package com.gffh.api.model;

import com.gffh.api.model.enums.TestAccountKind;

import java.time.Instant;

/**
 * A record that a {@link User} was generated for testing, kept in its own
 * collection so test data can be listed and removed without searching (or
 * touching) real accounts. The user, club and team themselves stay in their
 * normal collections so the account signs in and behaves like any other.
 */
public record TestAccount(
        String id,
        String userId,
        String email,
        String displayName,
        TestAccountKind kind,
        String clubId,
        String teamId,
        String createdByAdminId,
        Instant createdAt) {
}
