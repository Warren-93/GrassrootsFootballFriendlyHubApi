package com.gffh.api.domain;

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
public record MemberInvite(
        String id,
        String email,
        String teamId,
        Role role,
        String invitedByUserId,
        Instant createdAt,
        Instant expiresAt) {

    public static final Duration VALIDITY = Duration.ofDays(30);

    public boolean isPending(Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }
}
