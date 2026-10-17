package com.gffh.api.service;

import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.enums.Role;
import org.springframework.http.HttpStatus;

import java.util.List;

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

    /**
     * CLUB_ADMIN carries club-wide powers (venues, the club itself, every
     * squad), so only a club admin may grant it, or change or remove a
     * membership that has it - otherwise a team manager could promote
     * themselves. A 403 rather than a not-found: the caller already manages
     * the team, so there is nothing left to hide.
     */
    default void requireCanManageClubAdmins(String userId, String clubId) {
        Role role = roleForClub(userId, clubId);
        if (role == null || !role.canManageClub()) throw BusinessRuleException.clubAdminOnly();
    }

    Role roleForClub(String userId, String clubId);

    /**
     * Read-only reach over a team: any membership of the team itself (a plain
     * USER who joined with a code included), or any club-scoped membership of
     * its club. Enough to see the team's fixtures, never to act on them. The
     * public team profile needs none of this - see {@link TeamService#get}.
     */
    boolean canReadTeam(String userId, String teamId);

    default void requireCanReadTeam(String userId, String teamId) {
        if (!canReadTeam(userId, teamId)) {
            throw new BusinessRuleException("TEAM_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "That team could not be found.");
        }
    }

    /**
     * Whether this user manages the club itself (CLUB_ADMIN) or at least one
     * team it runs (TEAM_MANAGER) - the reach needed to see the club's venues
     * when arranging a game, though not to change them.
     */
    boolean canManageAnyTeamInClub(String userId, String clubId);

    default void requireCanManageAnyTeamInClub(String userId, String clubId) {
        if (!canManageAnyTeamInClub(userId, clubId)) {
            throw new BusinessRuleException("CLUB_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "That club could not be found.");
        }
    }

    /** Every user who can currently manage this team - direct team managers plus the club's admins. */
    List<String> managerUserIdsFor(String teamId);
}
