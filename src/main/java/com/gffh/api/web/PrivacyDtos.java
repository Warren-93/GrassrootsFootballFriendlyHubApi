package com.gffh.api.web;

import com.gffh.api.domain.User;

import java.time.Instant;
import java.util.List;

/** Wire types for SCR-PR-10 Privacy and data. */
public final class PrivacyDtos {

    private PrivacyDtos() {}

    public record MembershipExport(String membershipId, String role, String scope, String teamId, String teamName,
                                    String clubId, String clubName, Instant joinedAt) {}

    public record AccountExport(String userId, String email, String displayName, boolean emailVerified,
                                 Instant createdAt, List<MembershipExport> memberships) {

        public static AccountExport of(User user, List<MembershipExport> memberships) {
            return new AccountExport(user.id(), user.email(), user.displayName(), user.emailVerified(),
                    user.createdAt(), memberships);
        }
    }
}
