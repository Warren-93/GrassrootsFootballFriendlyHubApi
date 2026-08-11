package com.gffh.api.service;

import com.gffh.api.domain.Role;
import org.springframework.http.HttpStatus;

/**
 * Resolves what a user may do with a given team or club.
 *
 * <p>Team membership and role assignment are stored separately from the user
 * document (Technical Specification section 7), so entitlement is always a
 * lookup rather than a field on the principal. A user may hold different roles
 * in different clubs, which is why every check is scoped to a resource.
 */
public interface MembershipService {

    Role roleFor(String userId, String teamId);

    default void requireCanManageTeam(String userId, String teamId) {
        Role role = roleFor(userId, teamId);
        if (role == null || !role.canManageTeam()) {
            // Indistinguishable from a missing team, so membership cannot be
            // probed by iterating identifiers.
            throw new BusinessRuleException("TEAM_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "That team could not be found.");
        }
    }

    default void requireCanManageClub(String userId, String clubId) {
        Role role = roleForClub(userId, clubId);
        if (role == null || !role.canManageClub()) {
            throw new BusinessRuleException("CLUB_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "That club could not be found.");
        }
    }

    Role roleForClub(String userId, String clubId);
}
