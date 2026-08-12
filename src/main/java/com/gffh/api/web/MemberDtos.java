package com.gffh.api.web;

import com.gffh.api.domain.Membership;
import com.gffh.api.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Wire types for SCR-PR-04 Team members and permissions. */
public final class MemberDtos {

    private MemberDtos() {}

    /**
     * No email provider is connected (see VerificationTokenService), so this
     * adds an existing account directly rather than sending an invitation -
     * the target must already be registered.
     */
    public record AddMemberRequest(@NotBlank @Email String email, @NotBlank String role) {}

    public record UpdateRoleRequest(@NotBlank String role) {}

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
