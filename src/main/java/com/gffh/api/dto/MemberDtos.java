package com.gffh.api.dto;

import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.Membership;
import com.gffh.api.model.Team;
import com.gffh.api.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Wire types for SCR-PR-04 Team members and permissions. */
public final class MemberDtos {

    private MemberDtos() {}

    /**
     * Adds an existing account directly (200 with a {@link MemberView}). An
     * address with no account is invited instead (202 with an
     * {@link InvitedView}) and joins once its owner registers and verifies it.
     */
    public record AddMemberRequest(@NotBlank @Email String email, @NotBlank String role) {}

    /** A pending invitation to an address with no account yet. Expires 30 days after it was last sent. */
    public record InviteView(String id, String email, String role, String teamId, String teamName,
                             Instant createdAt, Instant expiresAt) {

        public static InviteView from(MemberInvite invite, Team team) {
            return new InviteView(invite.id(), invite.email(), invite.role().name(), invite.teamId(), team.name(),
                    invite.createdAt(), invite.expiresAt());
        }
    }

    /** The 202 body when adding by email sent an invitation instead; {@code status} is always "INVITED". */
    public record InvitedView(String status, InviteView invite) {

        public static InvitedView of(InviteView invite) {
            return new InvitedView("INVITED", invite);
        }
    }

    public record UpdateRoleRequest(@NotBlank String role) {}

    public record JoinCodeView(String code) {}

    public record RedeemJoinCodeRequest(@NotBlank String code) {}

    public record JoinResultView(String teamId, String teamName, String clubId, MemberView membership) {}

    public record MemberView(
            String membershipId, String userId, String email, String displayName,
            String role, String scope, Instant joinedAt) {

        /** scope is "TEAM" for a membership on this exact team, "CLUB" for a club-admin membership that cascades to it. */
        public static MemberView from(Membership m, User user) {
            return new MemberView(m.id(), user.id(), user.email(), user.displayName(),
                    m.role().name(), m.isClubScoped() ? "CLUB" : "TEAM", m.createdAt());
        }
    }
}
