package com.gffh.api.model;

import com.gffh.api.model.enums.TestAccountKind;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A record that a {@link User} was generated for testing, kept in its own
 * collection so test data can be listed and removed without searching (or
 * touching) real accounts. The user, club and team themselves stay in their
 * normal collections so the account signs in and behaves like any other.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestAccount {

    private String id;
    private String userId;
    private String email;
    private String displayName;
    private TestAccountKind kind;
    private String clubId;
    private String teamId;
    private String createdByAdminId;
    private Instant createdAt;
}
