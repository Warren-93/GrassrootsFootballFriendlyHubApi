package com.gffh.api.domain;

import java.time.Instant;

/**
 * Grants a user a {@link Role} over exactly one resource - a single team, or an
 * entire club. Club-scoped membership cascades to every team the club runs
 * ({@link com.gffh.api.service.MembershipService} resolves this), which is how a
 * club admin manages several squads without a membership row per team.
 */
public record Membership(
        String id,
        String userId,
        String teamId,
        String clubId,
        Role role,
        Instant createdAt) {

    public Membership {
        if ((teamId == null) == (clubId == null)) {
            throw new IllegalArgumentException(
                    "membership must be scoped to exactly one of teamId or clubId");
        }
    }

    public boolean isClubScoped() { return clubId != null; }
}
