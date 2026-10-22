package com.gffh.api.dto.admin;

import com.gffh.api.model.Membership;
import com.gffh.api.model.User;

import java.time.Instant;
import java.util.List;

public final class AdminUserDtos {

    private AdminUserDtos() {}

    public record MembershipView(String teamId, String clubId, String role) {
        public static MembershipView from(Membership m) {
            return new MembershipView(m.teamId(), m.clubId(), m.role().name());
        }
    }

    /** {@code lastActiveAt} is the last sign-in or app/session refresh; null if they've never signed in since sessions were recorded. */
    public record AdminUserView(
            String id, String email, String displayName, boolean emailVerified, boolean suspended,
            List<MembershipView> memberships, Instant createdAt, Instant lastActiveAt) {

        public static AdminUserView from(User u, List<Membership> memberships, Instant lastActiveAt) {
            return new AdminUserView(u.id(), u.email(), u.displayName(), u.emailVerified(), u.suspended(),
                    memberships.stream().map(MembershipView::from).toList(), u.createdAt(), lastActiveAt);
        }
    }
}
