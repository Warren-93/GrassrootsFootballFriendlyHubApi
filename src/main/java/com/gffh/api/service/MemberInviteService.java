package com.gffh.api.service;

import com.gffh.api.config.RateLimitProperties;
import com.gffh.api.dto.MemberDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.Membership;
import com.gffh.api.model.Team;
import com.gffh.api.model.User;
import com.gffh.api.model.enums.Role;
import com.gffh.api.repository.MemberInviteRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.security.RateLimiter;
import com.gffh.api.service.email.MemberEmails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SCR-PR-04's invitations: adding someone by an email address that has no
 * account yet. The invite is emailed with a link to register, and waits
 * (for {@link MemberInvite#VALIDITY}) until that address is verified - on the
 * emailed verification link, an admin's "verify email now", or Google/Apple
 * sign-in - when {@link #acceptPendingInvites} turns it into a membership.
 */
@Service
public class MemberInviteService {

    private static final Logger log = LoggerFactory.getLogger(MemberInviteService.class);

    private final MemberInviteRepository invites;
    private final MembershipRepository memberships;
    private final TeamRepository teams;
    private final UserRepository users;
    private final MembershipService membershipService;
    private final MemberEmails emails;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;

    public MemberInviteService(MemberInviteRepository invites, MembershipRepository memberships, TeamRepository teams,
                               UserRepository users, MembershipService membershipService, MemberEmails emails,
                               RateLimiter rateLimiter, RateLimitProperties rateLimits) {
        this.invites = invites;
        this.memberships = memberships;
        this.teams = teams;
        this.users = users;
        this.membershipService = membershipService;
        this.emails = emails;
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
    }

    /**
     * Creates the invite, or refreshes the existing one for this team and
     * address (new role, a fresh 30 days, the email sent again). The caller
     * has already checked the inviter may manage {@code team} (and its club,
     * for a CLUB_ADMIN invite - see MemberService#add) and that the address
     * has no account.
     *
     * <p>Invites email an address nobody has proved they own yet, so the
     * inviter must have verified their own email first, and each team can send
     * only so many an hour.
     */
    MemberDtos.InviteView invite(String inviterUserId, Team team, String email, Role role) {
        User inviter = users.findById(inviterUserId).orElseThrow(BusinessRuleException::blocked);
        if (!inviter.isEmailVerified()) throw BusinessRuleException.emailNotVerified();
        if (!rateLimiter.tryConsume("member-invite:" + team.getId(), rateLimits.getInvitationsPerHour())) {
            throw BusinessRuleException.rateLimited();
        }

        String address = email.trim().toLowerCase(Locale.ROOT);
        MemberInvite saved;
        try {
            saved = saveInvite(team, address, role, inviter);
        } catch (DuplicateKeyException e) {
            // A double-click raced us to the first invite: refresh that one instead.
            saved = saveInvite(team, address, role, inviter);
        }

        emails.sendInvite(saved, inviterName(inviter), team);
        return MemberDtos.InviteView.from(saved, team);
    }

    private MemberInvite saveInvite(Team team, String address, Role role, User inviter) {
        Instant now = Instant.now();
        String existingId = invites.findByTeamIdAndEmail(team.getId(), address).map(MemberInvite::getId).orElse(null);
        return invites.save(new MemberInvite(existingId, address, team.getId(), role, inviter.getId(),
                now, now.plus(MemberInvite.VALIDITY)));
    }

    /** Pending (unexpired) invites for the team, newest first. Same permission as listing members. */
    public List<MemberDtos.InviteView> list(String userId, String teamId) {
        membershipService.requireCanManageTeam(userId, teamId);
        Team team = teams.findById(teamId).orElseThrow(BusinessRuleException::blocked);
        return invites.findPendingByTeamId(teamId, Instant.now()).stream()
                .map(i -> MemberDtos.InviteView.from(i, team))
                .toList();
    }

    /** Withdraws an invite. Same permission as removing a member. */
    public void cancel(String userId, String teamId, String inviteId) {
        membershipService.requireCanManageTeam(userId, teamId);
        MemberInvite invite = invites.findById(inviteId)
                .filter(i -> i.getTeamId().equals(teamId))
                .orElseThrow(() -> new BusinessRuleException("INVITE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "That invitation could not be found."));
        invites.deleteById(invite.getId());
    }

    /**
     * Turns every pending invite for {@code user}'s email into a membership with
     * the invited role (club-wide for CLUB_ADMIN, this team otherwise), then
     * deletes those invites. Call it right after the email is marked verified:
     * on the verification link (only when it was emailed - see
     * AuthService#confirmVerification), an admin's "verify email now", and
     * Google or Apple sign-in. Does nothing for an unverified user, since an
     * unverified address proves nothing.
     *
     * <p>Teams the user already has a role on are skipped. Never throws: a
     * failure here must not fail the verification or sign-in that called it.
     *
     * @return the memberships created
     */
    public List<Membership> acceptPendingInvites(User user) {
        List<Membership> created = new ArrayList<>();
        if (user == null || !user.isEmailVerified() || user.getEmail() == null) return created;
        try {
            Instant now = Instant.now();
            for (MemberInvite invite : invites.findPendingByEmail(user.getEmail(), now)) {
                try {
                    Team team = teams.findById(invite.getTeamId()).orElse(null);
                    if (team != null && !hasRoleOn(user.getId(), team)) {
                        created.add(memberships.save(MemberService.scoped(null, user.getId(), team, invite.getRole(), now)));
                    }
                    invites.deleteById(invite.getId());
                } catch (Exception e) {
                    log.warn("Could not accept invite {} for user {}", invite.getId(), user.getId(), e);
                }
            }
            if (!created.isEmpty()) log.info("Accepted {} team invite(s) for user {}", created.size(), user.getId());
        } catch (Exception e) {
            log.warn("Could not accept pending invites for user {}", user.getId(), e);
        }
        return created;
    }

    /** Forgets every invite addressed to {@code email} (account deletion). */
    public void deleteInvitesFor(String email) {
        if (email != null && !email.isBlank()) invites.deleteByEmail(email);
    }

    private boolean hasRoleOn(String userId, Team team) {
        return memberships.findByUserIdAndTeamId(userId, team.getId()).isPresent()
                || memberships.findByUserIdAndClubId(userId, team.getClubId()).isPresent();
    }

    static String inviterName(User inviter) {
        return inviter.getDisplayName() == null || inviter.getDisplayName().isBlank()
                ? "A Kickabout user" : inviter.getDisplayName().trim();
    }
}
