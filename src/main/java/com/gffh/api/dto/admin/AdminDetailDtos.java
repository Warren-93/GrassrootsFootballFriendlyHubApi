package com.gffh.api.dto.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The admin site's user and team detail pages: everything held on them, and a dated timeline of what they did. */
public final class AdminDetailDtos {

    private AdminDetailDtos() {}

    /** One dated thing that happened. {@code kind} is a stable code for an icon/filter; {@code text} reads on its own. */
    public record ActivityEvent(Instant at, String kind, String text) {}

    /** A membership, with names resolved. {@code teamId}/{@code teamName} are null for a club-level one. */
    public record MemberView(String membershipId, String userId, String displayName, String email, String role,
                             String teamId, String teamName, String clubId, String clubName, Instant joinedAt) {}

    public record AdminUserDetail(
            AdminUserDtos.AdminUserView user,
            List<MemberView> memberships,
            int activeSessions,
            int pushDevices,
            long notifications,
            long chatMessagesSent,
            List<ActivityEvent> timeline) {}

    public record ClubSummary(String id, String name, String contactEmail, String website, String postcode,
                              Instant createdAt, List<String> teamNames, int venues) {}

    public record AdminTeamDetail(
            AdminTeamDtos.AdminTeamView team,
            String managerName,
            String contactPhone,
            String description,
            int travelRadiusMiles,
            String homeAwayPreference,
            boolean searchVisible,
            boolean shareContactDetails,
            Instant firstFixtureConfirmedAt,
            ClubSummary club,
            List<MemberView> members,
            /** e.g. "Availability slots", "Requests: CONFIRMED", "Fixtures: COMPLETED", "Chats", "Chat messages". */
            Map<String, Long> counts,
            List<ActivityEvent> timeline) {}
}
