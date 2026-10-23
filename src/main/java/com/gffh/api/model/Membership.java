package com.gffh.api.model;

import com.gffh.api.model.enums.Role;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Grants a user a {@link Role} over exactly one resource - a single team, or an
 * entire club. Club-scoped membership cascades to every team the club runs
 * ({@link com.gffh.api.service.MembershipService} resolves this), which is how a
 * club admin manages several squads without a membership row per team.
 */
@Data
@NoArgsConstructor
public class Membership {

    private String id;
    private String userId;
    private String teamId;
    private String clubId;
    private Role role;
    private Instant createdAt;

    public Membership(String id, String userId, String teamId, String clubId, Role role, Instant createdAt) {
        if ((teamId == null) == (clubId == null)) {
            throw new IllegalArgumentException(
                    "membership must be scoped to exactly one of teamId or clubId");
        }
        this.id = id;
        this.userId = userId;
        this.teamId = teamId;
        this.clubId = clubId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public boolean isClubScoped() { return clubId != null; }
}
