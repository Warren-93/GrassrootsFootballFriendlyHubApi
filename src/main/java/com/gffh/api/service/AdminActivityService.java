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
        add(events, user.createdAt(), "ACCOUNT_CREATED", "Account created");
        sessions.stream().map(RefreshToken::createdAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .ifPresent(at -> add(events, at, "LAST_ACTIVE", "Last active (signed in or reopened the app)"));
        for (Membership m : own) {
            add(events, m.createdAt(), "JOINED", "Joined " + names.membershipPlace(m) + " as " + roleLabel(m.role().name()));
        }
        for (PushToken d : devices) {
            add(events, d.registeredAt(), "DEVICE", "Turned on notifications on " + d.platform() + " device");
        }
        for (VerificationToken t : activity.emailLinksFor(userId, 50)) {
            String what = t.purpose() == VerificationTokenPurpose.PASSWORD_RESET
                    ? "Password reset link sent" : "Email verification link sent";
            add(events, t.createdAt(), "EMAIL_LINK", what + (t.used() ? " (used)" : ""));
        }
        for (String requestId : activity.requestIdsCreatedBy(userId, 50)) {
            requests.findById(requestId).ifPresent(fr -> add(events, fr.createdAt(), "REQUEST_SENT",
                    "Sent a friendly request from " + names.team(fr.senderTeamId()) + " to "
                            + names.team(fr.recipientTeamId()) + " for " + fr.date() + " (now " + fr.status() + ")"));
        }
        for (Message msg : messages) {
            String to = conversations.findById(msg.conversationId())
                    .map(c -> names.team(c.otherTeam(msg.senderTeamId()))).orElse("another team");
            add(events, msg.createdAt(), "MESSAGE", "Sent a chat message as " + names.team(msg.senderTeamId()) + " to " + to);
        }
        for (VerificationRequest v : activity.verificationRequestsSubmittedBy(userId)) {
            add(events, v.submittedAt(), "VERIFICATION", "Asked for " + names.team(v.teamId())
                    + " to be verified (" + v.status() + ")");
        }
        for (MemberInvite i : activity.invitesSentBy(userId, 50)) {
            add(events, i.createdAt(), "INVITE", "Invited " + i.email() + " to " + names.team(i.teamId())
                    + " as " + roleLabel(i.role().name()));
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
        names.teams.put(team.id(), team.name());
        List<FriendlyRequest> teamRequests = requests.findByTeamId(teamId);
        List<Fixture> teamFixtures = fixtures.findByTeamId(teamId);
        List<Conversation> chats = conversations.findByTeamId(teamId);
        List<Membership> members = new ArrayList<>(memberships.findByTeamId(teamId));
        Optional<Club> club = clubs.findById(team.clubId());

        List<AdminDetailDtos.ActivityEvent> events = new ArrayList<>();
        add(events, team.createdAt(), "TEAM_CREATED", "Team created");
        if (team.updatedAt() != null && !team.updatedAt().equals(team.createdAt())) {
            add(events, team.updatedAt(), "PROFILE_UPDATED", "Profile last updated");
        }
        add(events, team.firstFixtureConfirmedAt(), "FIRST_FIXTURE", "First fixture confirmed");
        for (Membership m : members) {
            add(events, m.createdAt(), "MEMBER_JOINED", names.user(m.userId()) + " joined as " + roleLabel(m.role().name()));
        }
        for (FriendlyRequest fr : teamRequests) {
            boolean sent = fr.senderTeamId().equals(teamId);
            String other = names.team(sent ? fr.recipientTeamId() : fr.senderTeamId());
            add(events, fr.createdAt(), sent ? "REQUEST_SENT" : "REQUEST_RECEIVED",
                    (sent ? "Sent a friendly request to " : "Received a friendly request from ") + other + " for " + fr.date()
                            + (fr.createdByUserId() != null && sent ? " (by " + names.user(fr.createdByUserId()) + ")" : ""));
            if (fr.updatedAt() != null && !fr.updatedAt().equals(fr.createdAt())) {
                add(events, fr.updatedAt(), "REQUEST_UPDATED", "Request with " + other + " for " + fr.date() + " moved to " + fr.status()
                        + (fr.actionReason() != null && !fr.actionReason().isBlank() ? ": " + fr.actionReason() : ""));
            }
        }
        for (Fixture f : teamFixtures) {
            String other = names.team(f.homeTeamId().equals(teamId) ? f.awayTeamId() : f.homeTeamId());
            add(events, f.createdAt(), "FIXTURE", "Fixture against " + other + " on " + f.date() + " confirmed (now " + f.status() + ")");
        }
        for (Conversation c : chats) {
            add(events, c.createdAt(), "CHAT_STARTED", "Chat started with " + names.team(c.otherTeam(teamId)));
            add(events, c.lastMessageAt(), "CHAT_LAST_MESSAGE", "Latest chat message with " + names.team(c.otherTeam(teamId)));
        }
        for (VerificationRequest v : activity.verificationRequestsFor(teamId)) {
            add(events, v.submittedAt(), "VERIFICATION", "Verification asked for by " + names.user(v.submittedByUserId()));
            add(events, v.reviewedAt(), "VERIFICATION_REVIEWED", "Verification " + v.status());
        }
        List<Report> reports = activity.reportsInvolving(teamId);
        for (Report r : reports) {
            boolean made = teamId.equals(r.reporterTeamId());
            add(events, r.createdAt(), made ? "REPORT_MADE" : "REPORT_RECEIVED",
                    (made ? "Reported " + names.team(r.reportedTeamId()) : "Reported by " + names.team(r.reporterTeamId()))
                            + " (" + r.type() + ", " + r.status() + ")");
        }
        List<Block> blocks = activity.blocksInvolving(teamId);
        for (Block b : blocks) {
            boolean made = teamId.equals(b.blockingTeamId());
            add(events, b.createdAt(), made ? "BLOCKED" : "BLOCKED_BY",
                    made ? "Blocked " + names.team(b.blockedTeamId()) : "Blocked by " + names.team(b.blockingTeamId()));
        }
        adminActions(events, "TEAM", teamId);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("Availability slots", activity.availabilitySlotsFor(teamId));
        teamRequests.forEach(fr -> counts.merge("Requests: " + fr.status(), 1L, Long::sum));
        teamFixtures.forEach(f -> counts.merge("Fixtures: " + f.status(), 1L, Long::sum));
        counts.put("Chats", (long) chats.size());
        counts.put("Chat messages", activity.messagesIn(chats.stream().map(Conversation::id).toList()));
        counts.put("Blocks", (long) blocks.size());
        counts.put("Reports", (long) reports.size());

        List<AdminDetailDtos.MemberView> memberViews = new ArrayList<>();
        members.forEach(m -> memberViews.add(names.member(m, null)));
        memberships.findByClubId(team.clubId()).forEach(m -> memberViews.add(names.member(m, null)));

        AdminDetailDtos.ClubSummary clubSummary = club.map(c -> new AdminDetailDtos.ClubSummary(c.id(), c.name(),
                c.contactEmail(), c.website(), c.postcode(), c.createdAt(),
                teams.findByClubId(c.id()).stream().map(Team::name).toList(),
                venues.findByClubId(c.id()).size())).orElse(null);

        return new AdminDetailDtos.AdminTeamDetail(AdminTeamDtos.AdminTeamView.from(team), team.managerName(),
                team.contactPhone(), team.description(), team.travelRadiusMiles(),
                team.homeAwayPreference() == null ? null : team.homeAwayPreference().name(),
                team.searchVisible(), team.shareContactDetails(), team.firstFixtureConfirmedAt(), clubSummary,
                memberViews, counts, newestFirst(events));
    }

    private void adminActions(List<AdminDetailDtos.ActivityEvent> events, String targetType, String targetId) {
        for (AuditLogEntry e : auditLog.list(targetType, targetId, 100)) {
            add(events, e.timestamp(), "ADMIN", "Admin " + e.actorEmail() + ": " + e.action()
                    + (e.reason() != null && !e.reason().isBlank() ? " - " + e.reason() : ""));
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
                    .map(Team::name).orElse("a deleted team"));
        }

        String club(String id) {
            if (id == null) return "a deleted club";
            return clubs.computeIfAbsent(id, k -> AdminActivityService.this.clubs.findById(k)
                    .map(Club::name).orElse("a deleted club"));
        }

        Optional<User> userRecord(String id) {
            return id == null ? Optional.empty()
                    : users.computeIfAbsent(id, k -> AdminActivityService.this.users.findById(k));
        }

        String user(String id) {
            return userRecord(id).map(User::displayName).orElse("A deleted user");
        }

        String membershipPlace(Membership m) {
            return m.isClubScoped() ? club(m.clubId()) + " (club)" : team(m.teamId());
        }

        AdminDetailDtos.MemberView member(Membership m, User known) {
            Optional<User> u = known != null ? Optional.of(known) : userRecord(m.userId());
            String clubId = m.isClubScoped() ? m.clubId()
                    : AdminActivityService.this.teams.findById(m.teamId()).map(Team::clubId).orElse(null);
            return new AdminDetailDtos.MemberView(m.id(), m.userId(), u.map(User::displayName).orElse(null),
                    u.map(User::email).orElse(null), m.role().name(), m.teamId(),
                    m.isClubScoped() ? null : team(m.teamId()), clubId, clubId == null ? null : club(clubId),
                    m.createdAt());
        }
    }
}
