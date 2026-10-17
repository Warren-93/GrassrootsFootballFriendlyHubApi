package com.gffh.api.dto;

import com.gffh.api.model.AvailabilitySlot;
import com.gffh.api.model.Block;
import com.gffh.api.model.Fixture;
import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.NotificationPreference;
import com.gffh.api.model.User;

import java.time.Instant;
import java.util.List;

/** Wire types for SCR-PR-10 Privacy and data. */
public final class PrivacyDtos {

    private PrivacyDtos() {}

    public record MembershipExport(String membershipId, String role, String scope, String teamId, String teamName,
                                    String clubId, String clubName, Instant joinedAt) {}

    /** Everything held about one team the user manages. */
    public record TeamExport(TeamDtos.TeamView team, List<VenueDtos.VenueView> venues,
                             List<AvailabilitySlot> availability, List<FriendlyRequest> friendlyRequests,
                             List<Fixture> fixtures, List<Block> blockedTeams) {}

    public record AccountExport(String userId, String email, String displayName, boolean emailVerified,
                                 Instant createdAt, List<MembershipExport> memberships,
                                 NotificationPreference notificationPreferences, List<TeamExport> teams) {

        public static AccountExport of(User user, List<MembershipExport> memberships,
                                       NotificationPreference notificationPreferences, List<TeamExport> teams) {
            return new AccountExport(user.id(), user.email(), user.displayName(), user.emailVerified(),
                    user.createdAt(), memberships, notificationPreferences, teams);
        }
    }
}
