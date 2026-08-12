package com.gffh.api.web;

import com.gffh.api.domain.Membership;
import com.gffh.api.domain.User;

import java.util.List;

public final class AdminUserDtos {

    private AdminUserDtos() {}

    public record MembershipView(String teamId, String clubId, String role) {
        public static MembershipView from(Membership m) {
            return new MembershipView(m.teamId(), m.clubId(), m.role().name());
        }
    }

    public record AdminUserView(
            String id, String email, String displayName, boolean emailVerified, boolean suspended,
            List<MembershipView> memberships) {

        public static AdminUserView from(User u, List<Membership> memberships) {
            return new AdminUserView(u.id(), u.email(), u.displayName(), u.emailVerified(), u.suspended(),
                    memberships.stream().map(MembershipView::from).toList());
        }
    }
}
