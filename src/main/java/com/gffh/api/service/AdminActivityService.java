package com.gffh.api.service;

import com.gffh.api.dto.admin.AdminDetailDtos;
import com.gffh.api.dto.admin.AdminTeamDtos;
import com.gffh.api.dto.admin.AdminUserDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.AuditLogEntry;
import com.gffh.api.model.Block;
import com.gffh.api.model.Club;
import com.gffh.api.model.Conversation;
import com.gffh.api.model.Fixture;
import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.Membership;
import com.gffh.api.model.Message;
import com.gffh.api.model.PushToken;
import com.gffh.api.model.RefreshToken;
import com.gffh.api.model.Report;
import com.gffh.api.model.Team;
import com.gffh.api.model.User;
import com.gffh.api.model.VerificationRequest;
import com.gffh.api.model.VerificationToken;
import com.gffh.api.model.enums.VerificationTokenPurpose;
import com.gffh.api.repository.AdminActivityRepository;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.ConversationRepository;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.PushTokenRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.repository.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the admin detail pages for a user and a team: what's held on them
 * and a newest-first timeline pieced together from the timestamps already
 * stored on each record (account created, joined a team, sent a request,
 * chatted, asked for verification, admin actions, ...). Nothing new is
 * recorded to make it - so it reaches back to before this page existed.
 */
@Service
public class AdminActivityService {

    private static final int TIMELINE_LIMIT = 200;

    private final UserRepository users;
    private final TeamRepository teams;
    private final ClubRepository clubs;
    private final MembershipRepository memberships;
    private final FriendlyRequestRepository requests;
    private final FixtureRepository fixtures;
    private final ConversationRepository conversations;
    private final VenueRepository venues;
    private final PushTokenRepository pushTokens;
    private final AdminActivityRepository activity;
    private final AuditLogService auditLog;

    public AdminActivityService(UserRepository users, TeamRepository teams, ClubRepository clubs,
                                MembershipRepository memberships, FriendlyRequestRepository requests,
                                FixtureRepository fixtures, ConversationRepository conversations,
                                VenueRepository venues, PushTokenRepository pushTokens,
                                AdminActivityRepository activity, AuditLogService auditLog) {
        this.users = users;
        this.teams = teams;
        this.clubs = clubs;
        this.memberships = memberships;
        this.requests = requests;
        this.fixtures = fixtures;
        this.conversations = conversations;
        this.venues = venues;
        this.pushTokens = pushTokens;
        this.activity = activity;
        this.auditLog = auditLog;
    }

    public AdminDetailDtos.AdminUserDetail userDetail(String userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND, "That account could not be found."));
        Names names = new Names();
        List<Membership> own = memberships.findByUserId(userId);
        List<RefreshToken> sessions = activity.sessionsFor(userId, 200);
        List<PushToken> devices = pushTokens.findByUserId(userId);
        List<Message> messages = activity.messagesSentBy(userId, 50);
        Instant now = Instant.now();

        List<AdminDetailDtos.ActivityEvent> events = new ArrayList<>();
        add(events, user.getCreatedAt(), "ACCOUNT_CREATED", "Account created");
        sessions.stream().map(RefreshToken::getCreatedAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .ifPresent(at -> add(events, at, "LAST_ACTIVE", "Last active (signed in or reopened the app)"));
        for (Membership m : own) {
            add(events, m.getCreatedAt(), "JOINED", "Joined " + names.membershipPlace(m) + " as " + roleLabel(m.getRole().name()));
        }
        for (PushToken d : devices) {
            add(events, d.getRegisteredAt(), "DEVICE", "Turned on notifications on " + d.getPlatform() + " device");
        }
        for (VerificationToken t : activity.emailLinksFor(userId, 50)) {
            String what = t.getPurpose() == VerificationTokenPurpose.PASSWORD_RESET
                    ? "Password reset link sent" : "Email verification link sent";
            add(events, t.getCreatedAt(), "EMAIL_LINK", what + (t.isUsed() ? " (used)" : ""));
        }
        for (String requestId : activity.requestIdsCreatedBy(userId, 50)) {
            requests.findById(requestId).ifPresent(fr -> add(events, fr.getCreatedAt(), "REQUEST_SENT",
                    "Sent a friendly request from " + names.team(fr.getSenderTeamId()) + " to "
                            + names.team(fr.getRecipientTeamId()) + " for " + fr.getDate() + " (now " + fr.getStatus() + ")"));
        }
        for (Message msg : messages) {
            String to = conversations.findById(msg.getConversationId())
                    .map(c -> names.team(c.otherTeam(msg.getSenderTeamId()))).orElse("another team");
            add(events, msg.getCreatedAt(), "MESSAGE", "Sent a chat message as " + names.team(msg.getSenderTeamId()) + " to " + to);
        }
        for (VerificationRequest v : activity.verificationRequestsSubmittedBy(userId)) {
            add(events, v.getSubmittedAt(), "VERIFICATION", "Asked for " + names.team(v.getTeamId())
                    + " to be verified (" + v.getStatus() + ")");
        }
        for (MemberInvite i : activity.invitesSentBy(userId, 50)) {
            add(events, i.getCreatedAt(), "INVITE", "Invited " + i.getEmail() + " to " + names.team(i.getTeamId())
                    + " as " + roleLabel(i.getRole().name()));
        }
        adminActions(events, "USER", userId);

        AdminUserDtos.AdminUserView view = AdminUserDtos.AdminUserView.from(user, own,
                activity.lastActiveAt(List.of(userId)).get(userId));
        int active = (int) sessions.stream().filter(s -> s.isUsable(now)).count();
        return new AdminDetailDtos.AdminUserDetail(view, own.stream().map(m -> names.member(m, user)).toList(),
                active, devices.size(), activity.notificationsFor(userId), messages.size(), newestFirst(events));
    }

    public AdminDetailDtos.AdminTeamDetail teamDetail(String teamId) {
        Team team = teams.findById(teamId).orElseThrow(() -> new BusinessRuleException(
                "TEAM_NOT_FOUND", HttpStatus.NOT_FOUND, "That team could not be found."));
        Names names = new Names();
        names.teams.put(team.getId(), team.getName());
        List<FriendlyRequest> teamRequests = requests.findByTeamId(teamId);
        List<Fixture> teamFixtures = fixtures.findByTeamId(teamId);
        List<Conversation> chats = conversations.findByTeamId(teamId);
        List<Membership> members = new ArrayList<>(memberships.findByTeamId(teamId));
        Optional<Club> club = clubs.findById(team.getClubId());

        List<AdminDetailDtos.ActivityEvent> events = new ArrayList<>();
        add(events, team.getCreatedAt(), "TEAM_CREATED", "Team created");
        if (team.getUpdatedAt() != null && !team.getUpdatedAt().equals(team.getCreatedAt())) {
            add(events, team.getUpdatedAt(), "PROFILE_UPDATED", "Profile last updated");
        }
        add(events, team.getFirstFixtureConfirmedAt(), "FIRST_FIXTURE", "First fixture confirmed");
        for (Membership m : members) {
            add(events, m.getCreatedAt(), "MEMBER_JOINED", names.user(m.getUserId()) + " joined as " + roleLabel(m.getRole().name()));
        }
        for (FriendlyRequest fr : teamRequests) {
            boolean sent = fr.getSenderTeamId().equals(teamId);
            String other = names.team(sent ? fr.getRecipientTeamId() : fr.getSenderTeamId());
            add(events, fr.getCreatedAt(), sent ? "REQUEST_SENT" : "REQUEST_RECEIVED",
                    (sent ? "Sent a friendly request to " : "Received a friendly request from ") + other + " for " + fr.getDate()
                            + (fr.getCreatedByUserId() != null && sent ? " (by " + names.user(fr.getCreatedByUserId()) + ")" : ""));
            if (fr.getUpdatedAt() != null && !fr.getUpdatedAt().equals(fr.getCreatedAt())) {
                add(events, fr.getUpdatedAt(), "REQUEST_UPDATED", "Request with " + other + " for " + fr.getDate() + " moved to " + fr.getStatus()
                        + (fr.getActionReason() != null && !fr.getActionReason().isBlank() ? ": " + fr.getActionReason() : ""));
            }
        }
        for (Fixture f : teamFixtures) {
            String other = names.team(f.getHomeTeamId().equals(teamId) ? f.getAwayTeamId() : f.getHomeTeamId());
            add(events, f.getCreatedAt(), "FIXTURE", "Fixture against " + other + " on " + f.getDate() + " confirmed (now " + f.getStatus() + ")");
        }
        for (Conversation c : chats) {
            add(events, c.getCreatedAt(), "CHAT_STARTED", "Chat started with " + names.team(c.otherTeam(teamId)));
            add(events, c.getLastMessageAt(), "CHAT_LAST_MESSAGE", "Latest chat message with " + names.team(c.otherTeam(teamId)));
        }
        for (VerificationRequest v : activity.verificationRequestsFor(teamId)) {
            add(events, v.getSubmittedAt(), "VERIFICATION", "Verification asked for by " + names.user(v.getSubmittedByUserId()));
            add(events, v.getReviewedAt(), "VERIFICATION_REVIEWED", "Verification " + v.getStatus());
        }
        List<Report> reports = activity.reportsInvolving(teamId);
        for (Report r : reports) {
            boolean made = teamId.equals(r.getReporterTeamId());
            add(events, r.getCreatedAt(), made ? "REPORT_MADE" : "REPORT_RECEIVED",
                    (made ? "Reported " + names.team(r.getReportedTeamId()) : "Reported by " + names.team(r.getReporterTeamId()))
                            + " (" + r.getType() + ", " + r.getStatus() + ")");
        }
        List<Block> blocks = activity.blocksInvolving(teamId);
        for (Block b : blocks) {
            boolean made = teamId.equals(b.getBlockingTeamId());
            add(events, b.getCreatedAt(), made ? "BLOCKED" : "BLOCKED_BY",
                    made ? "Blocked " + names.team(b.getBlockedTeamId()) : "Blocked by " + names.team(b.getBlockingTeamId()));
        }
        adminActions(events, "TEAM", teamId);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("Availability slots", activity.availabilitySlotsFor(teamId));
        teamRequests.forEach(fr -> counts.merge("Requests: " + fr.getStatus(), 1L, Long::sum));
        teamFixtures.forEach(f -> counts.merge("Fixtures: " + f.getStatus(), 1L, Long::sum));
        counts.put("Chats", (long) chats.size());
        counts.put("Chat messages", activity.messagesIn(chats.stream().map(Conversation::getId).toList()));
        counts.put("Blocks", (long) blocks.size());
        counts.put("Reports", (long) reports.size());

        List<AdminDetailDtos.MemberView> memberViews = new ArrayList<>();
        members.forEach(m -> memberViews.add(names.member(m, null)));
        memberships.findByClubId(team.getClubId()).forEach(m -> memberViews.add(names.member(m, null)));

        AdminDetailDtos.ClubSummary clubSummary = club.map(c -> new AdminDetailDtos.ClubSummary(c.getId(), c.getName(),
                c.getContactEmail(), c.getWebsite(), c.getPostcode(), c.getCreatedAt(),
                teams.findByClubId(c.getId()).stream().map(Team::getName).toList(),
                venues.findByClubId(c.getId()).size())).orElse(null);

        return new AdminDetailDtos.AdminTeamDetail(AdminTeamDtos.AdminTeamView.from(team), team.getManagerName(),
                team.getContactPhone(), team.getDescription(), team.getTravelRadiusMiles(),
                team.getHomeAwayPreference() == null ? null : team.getHomeAwayPreference().name(),
                team.isSearchVisible(), team.isShareContactDetails(), team.getFirstFixtureConfirmedAt(), clubSummary,
                memberViews, counts, newestFirst(events));
    }

    private void adminActions(List<AdminDetailDtos.ActivityEvent> events, String targetType, String targetId) {
        for (AuditLogEntry e : auditLog.list(targetType, targetId, 100)) {
            add(events, e.getTimestamp(), "ADMIN", "Admin " + e.getActorEmail() + ": " + e.getAction()
                    + (e.getReason() != null && !e.getReason().isBlank() ? " - " + e.getReason() : ""));
        }
    }

    private static void add(List<AdminDetailDtos.ActivityEvent> events, Instant at, String kind, String text) {
        if (at != null) events.add(new AdminDetailDtos.ActivityEvent(at, kind, text));
    }

    private static List<AdminDetailDtos.ActivityEvent> newestFirst(List<AdminDetailDtos.ActivityEvent> events) {
        return events.stream()
                .sorted(Comparator.comparing(AdminDetailDtos.ActivityEvent::at).reversed())
                .limit(TIMELINE_LIMIT)
                .toList();
    }

    private static String roleLabel(String role) {
        return switch (role) {
            case "CLUB_ADMIN" -> "club admin";
            case "TEAM_MANAGER" -> "team manager";
            default -> role.toLowerCase().replace('_', ' ');
        };
    }

    /** Looks each team, club and user up once per page, however often it's mentioned. */
    private final class Names {
        final Map<String, String> teams = new HashMap<>();
        final Map<String, Optional<User>> users = new HashMap<>();
        final Map<String, String> clubs = new HashMap<>();

        String team(String id) {
            if (id == null) return "a deleted team";
            return teams.computeIfAbsent(id, k -> AdminActivityService.this.teams.findById(k)
                    .map(Team::getName).orElse("a deleted team"));
        }

        String club(String id) {
            if (id == null) return "a deleted club";
            return clubs.computeIfAbsent(id, k -> AdminActivityService.this.clubs.findById(k)
                    .map(Club::getName).orElse("a deleted club"));
        }

        Optional<User> userRecord(String id) {
            return id == null ? Optional.empty()
                    : users.computeIfAbsent(id, k -> AdminActivityService.this.users.findById(k));
        }

        String user(String id) {
            return userRecord(id).map(User::getDisplayName).orElse("A deleted user");
        }

        String membershipPlace(Membership m) {
            return m.isClubScoped() ? club(m.getClubId()) + " (club)" : team(m.getTeamId());
        }

        AdminDetailDtos.MemberView member(Membership m, User known) {
            Optional<User> u = known != null ? Optional.of(known) : userRecord(m.getUserId());
            String clubId = m.isClubScoped() ? m.getClubId()
                    : AdminActivityService.this.teams.findById(m.getTeamId()).map(Team::getClubId).orElse(null);
            return new AdminDetailDtos.MemberView(m.getId(), m.getUserId(), u.map(User::getDisplayName).orElse(null),
                    u.map(User::getEmail).orElse(null), m.getRole().name(), m.getTeamId(),
                    m.isClubScoped() ? null : team(m.getTeamId()), clubId, clubId == null ? null : club(clubId),
                    m.getCreatedAt());
        }
    }
}
