package com.gffh.api.service;

import com.gffh.api.domain.JoinCode;
import com.gffh.api.domain.Membership;
import com.gffh.api.domain.Role;
import com.gffh.api.domain.Team;
import com.gffh.api.domain.User;
import com.gffh.api.repository.JoinCodeRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.web.MemberDtos;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * SCR-PR-04 Team members and permissions. Purpose: control who can act on
 * behalf of a team - a safeguarding control as much as an admin one.
 */
@Service
public class MemberService {

    /** Excludes 0/O and 1/I/L - a code gets read aloud and typed by hand as often as it gets pasted. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MembershipRepository memberships;
    private final TeamRepository teams;
    private final UserRepository users;
    private final MembershipService membershipService;
    private final JoinCodeRepository joinCodes;

    public MemberService(MembershipRepository memberships, TeamRepository teams, UserRepository users,
                         MembershipService membershipService, JoinCodeRepository joinCodes) {
        this.memberships = memberships;
        this.teams = teams;
        this.users = users;
        this.membershipService = membershipService;
        this.joinCodes = joinCodes;
    }

    /** Direct team memberships plus the club's CLUB_ADMIN memberships, which cascade to every squad the club runs. */
    public List<MemberDtos.MemberView> list(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team team = findTeam(teamId);

        Stream<Membership> teamScoped = memberships.findByTeamId(teamId).stream();
        Stream<Membership> clubScoped = memberships.findByClubId(team.clubId()).stream();

        return Stream.concat(teamScoped, clubScoped)
                .map(m -> users.findById(m.userId()).map(user -> MemberDtos.MemberView.from(m, user)).orElse(null))
                .filter(v -> v != null)
                .toList();
    }

    public MemberDtos.MemberView add(String userId, String teamId, MemberDtos.AddMemberRequest request) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team team = findTeam(teamId);

        User target = users.findByEmail(request.email()).orElseThrow(() -> new BusinessRuleException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND,
                "No account found with that email - they'll need to register before they can be added."));

        Role role = parseRole(request.role());
        if (memberships.findTeamMembership(target.id(), teamId).isPresent()
                || memberships.findClubMembership(target.id(), team.clubId()).isPresent()) {
            throw new BusinessRuleException("ALREADY_A_MEMBER", HttpStatus.CONFLICT,
                    "This person already has a role on this team.");
        }

        Membership saved = memberships.save(scoped(null, target.id(), team, role, Instant.now()));
        return MemberDtos.MemberView.from(saved, target);
    }

    public MemberDtos.MemberView updateRole(String userId, String teamId, String membershipId,
                                            MemberDtos.UpdateRoleRequest request) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team team = findTeam(teamId);
        Membership current = findMembershipForTeam(teamId, membershipId);
        Role newRole = parseRole(request.role());

        if (current.role() == Role.CLUB_ADMIN && newRole != Role.CLUB_ADMIN) {
            requireNotLastClubAdmin(team.clubId(), membershipId);
        }

        Membership updated = scoped(current.id(), current.userId(), team, newRole, current.createdAt());
        Membership saved = memberships.save(updated);
        User user = users.findById(saved.userId()).orElseThrow(BusinessRuleException::blocked);
        return MemberDtos.MemberView.from(saved, user);
    }

    public void remove(String userId, String teamId, String membershipId) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team team = findTeam(teamId);
        Membership current = findMembershipForTeam(teamId, membershipId);

        if (current.role() == Role.CLUB_ADMIN) {
            requireNotLastClubAdmin(team.clubId(), membershipId);
        }

        memberships.delete(membershipId);
    }

    /**
     * The team's current join code, generating one on first request - the
     * self-service alternative to {@link #add}, for a manager who wants to
     * hand out one code rather than look up each player's account by email.
     */
    public String getOrCreateJoinCode(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        return joinCodes.findByTeamId(teamId).map(JoinCode::code)
                .orElseGet(() -> regenerateJoinCodeUnchecked(userId, teamId));
    }

    /** Invalidates any existing code for this team and issues a fresh one. */
    public String regenerateJoinCode(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        return regenerateJoinCodeUnchecked(userId, teamId);
    }

    private String regenerateJoinCodeUnchecked(String userId, String teamId) {
        joinCodes.deleteByTeamId(teamId);
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return joinCodes.save(new JoinCode(null, teamId, generateCode(), userId, Instant.now())).code();
            } catch (DuplicateKeyException e) {
                // Codes collide with negligible probability (32^6 combinations) - retry with a fresh one.
            }
        }
        throw new BusinessRuleException("JOIN_CODE_GENERATION_FAILED", HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not generate a join code. Please try again.");
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        return sb.toString();
    }

    /**
     * Redeems a join code: adds the calling user as a basic member of
     * whichever team it belongs to. Returns which team was joined (not just
     * the membership) since the client needs it to switch straight into
     * that team - {@link MemberDtos.MemberView} alone carries no team
     * identity, only meaningful in the context of an already-known team.
     */
    public MemberDtos.JoinResultView redeem(String userId, String rawCode) {
        String code = rawCode.trim().toUpperCase();
        JoinCode joinCode = joinCodes.findByCode(code).orElseThrow(() -> new BusinessRuleException(
                "JOIN_CODE_NOT_FOUND", HttpStatus.NOT_FOUND, "That code is not valid."));
        Team team = findTeam(joinCode.teamId());
        User target = users.findById(userId).orElseThrow(BusinessRuleException::blocked);

        if (memberships.findTeamMembership(userId, team.id()).isPresent()
                || memberships.findClubMembership(userId, team.clubId()).isPresent()) {
            throw new BusinessRuleException("ALREADY_A_MEMBER", HttpStatus.CONFLICT,
                    "You're already a member of this team.");
        }

        Membership saved = memberships.save(scoped(null, userId, team, Role.USER, Instant.now()));
        return new MemberDtos.JoinResultView(team.id(), team.name(), team.clubId(),
                MemberDtos.MemberView.from(saved, target));
    }

    /** CLUB_ADMIN always cascades club-wide, so it is club-scoped; every other role is scoped to this exact team. */
    private static Membership scoped(String id, String userId, Team team, Role role, Instant createdAt) {
        return role == Role.CLUB_ADMIN
                ? new Membership(id, userId, null, team.clubId(), role, createdAt)
                : new Membership(id, userId, team.id(), null, role, createdAt);
    }

    private void requireNotLastClubAdmin(String clubId, String excludingMembershipId) {
        boolean anotherAdminExists = memberships.findByClubId(clubId).stream()
                .anyMatch(m -> m.role() == Role.CLUB_ADMIN && !m.id().equals(excludingMembershipId));
        if (!anotherAdminExists) {
            throw new BusinessRuleException("LAST_CLUB_ADMIN", HttpStatus.CONFLICT,
                    "This is the club's only admin - promote someone else first.");
        }
    }

    private Membership findMembershipForTeam(String teamId, String membershipId) {
        Membership m = memberships.findById(membershipId).orElseThrow(() -> new BusinessRuleException(
                "MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND, "That member could not be found."));
        Team team = findTeam(teamId);
        boolean belongsHere = teamId.equals(m.teamId()) || team.clubId().equals(m.clubId());
        if (!belongsHere) {
            throw new BusinessRuleException("MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "That member could not be found.");
        }
        return m;
    }

    private Team findTeam(String teamId) {
        return teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
    }

    private static Role parseRole(String raw) {
        try {
            Role role = Role.valueOf(raw);
            if (role == Role.PLATFORM_ADMIN) throw new IllegalArgumentException();
            return role;
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("INVALID_ROLE", HttpStatus.BAD_REQUEST,
                    "Role must be one of USER, TEAM_MANAGER, CLUB_ADMIN.");
        }
    }
}
