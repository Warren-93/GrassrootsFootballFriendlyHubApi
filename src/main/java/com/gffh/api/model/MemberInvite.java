package com.gffh.api.model;

import com.gffh.api.model.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * SCR-PR-04: a manager asked to add an email address that has no account yet.
 * The invite waits until someone proves they own that address (verifies it),
 * then becomes a real {@link Membership} with this role - never on mere
 * registration, since an unverified address proves nothing.
 *
 * <p>One invite per team and address; inviting again refreshes it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberInvite {

    private String id;
    private String email;
    private String teamId;
    private Role role;
    private String invitedByUserId;
    private Instant createdAt;
    private Instant expiresAt;

    public static final Duration VALIDITY = Duration.ofDays(30);

    public boolean isPending(Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }
}
