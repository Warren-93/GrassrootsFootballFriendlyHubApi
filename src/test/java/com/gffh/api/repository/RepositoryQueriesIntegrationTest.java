package com.gffh.api.repository;

import com.gffh.api.model.AvailabilitySlot;
import com.gffh.api.model.Club;
import com.gffh.api.model.Conversation;
import com.gffh.api.model.MatchingWeightsVersion;
import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.Notification;
import com.gffh.api.model.PushToken;
import com.gffh.api.model.RefreshToken;
import com.gffh.api.model.Report;
import com.gffh.api.model.User;
import com.gffh.api.model.Venue;
import com.gffh.api.model.enums.AvailabilityStatus;
import com.gffh.api.model.enums.HomeAwayPreference;
import com.gffh.api.model.enums.NotificationType;
import com.gffh.api.model.enums.PushPlatform;
import com.gffh.api.model.enums.ReportSeverity;
import com.gffh.api.model.enums.ReportStatus;
import com.gffh.api.model.enums.ReportType;
import com.gffh.api.model.enums.Role;
import com.gffh.api.service.matching.MatchingWeights;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The repository methods written as {@code @Query}/{@code @Update}
 * annotations, derived names or default methods, checked against a real
 * MongoDB - in particular the ones no HTTP-level test reaches.
 */
class RepositoryQueriesIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ClubRepository clubs;
    @Autowired private UserRepository users;
    @Autowired private MatchingWeightsVersionRepository weights;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private VenueRepository venues;
    @Autowired private NotificationRepository notifications;
    @Autowired private MemberInviteRepository invites;
    @Autowired private AvailabilityRepository availability;
    @Autowired private ConversationRepository conversations;
    @Autowired private ReportRepository reports;
    @Autowired private PushTokenRepository pushTokens;

    private static String run() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("a new document gets a UUID id and a created date; a given created date is kept")
    void saveFillsIdAndCreatedDate() {
        Club club = clubs.save(new Club(null, "Defaults " + run(), null, null, null, null, null, null));
        assertThat(UUID.fromString(club.getId())).isNotNull();
        assertThat(club.getCreatedAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));

        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Club kept = clubs.save(new Club(null, "Kept " + run(), null, null, null, null, null, earlier));
        assertThat(clubs.findById(kept.getId()).orElseThrow().getCreatedAt()).isEqualTo(earlier);
    }

    @Test
    @DisplayName("name and email searches are case-insensitive, treat the query literally and respect the limit")
    void searches() {
        String run = run();
        clubs.save(new Club(null, "Search (" + run + ") Rovers", null, null, null, null, null, null));
        clubs.save(new Club(null, "search (" + run + ") united", null, null, null, null, null, null));
        assertThat(clubs.searchByName("SEARCH (" + run + ")", 10)).hasSize(2);
        assertThat(clubs.searchByName("search (" + run + ")", 1)).hasSize(1);

        User user = users.save(new User(null, "Mixed." + run + "@Example.com", "hash", "Display " + run, true, null, false));
        assertThat(user.getEmail()).isEqualTo("mixed." + run + "@example.com");
        assertThat(users.findByEmail("MIXED." + run + "@example.COM")).isPresent();
        assertThat(users.search("display " + run, 5)).extracting(User::getId).containsExactly(user.getId());
        assertThat(users.search("mixed." + run, 5)).extracting(User::getId).containsExactly(user.getId());
    }

    @Test
    @DisplayName("only one matching weights version stays active, and versions list newest first")
    void matchingWeights() {
        MatchingWeights w = MatchingWeights.DEFAULT;
        weights.deactivateAll();
        weights.save(new MatchingWeightsVersion(null, w, "old", "admin", Instant.now().minusSeconds(60), true));
        weights.deactivateAll();
        MatchingWeightsVersion current = weights.save(new MatchingWeightsVersion(null, w, "new", "admin", Instant.now(), true));

        assertThat(weights.findFirstByActiveTrue()).get().extracting(MatchingWeightsVersion::getId).isEqualTo(current.getId());
        assertThat(weights.findFirstByActiveTrue().orElseThrow().getWeights()).isEqualTo(w);
        assertThat(weights.findAllByOrderByPublishedAtDesc().get(0).getId()).isEqualTo(current.getId());
    }

    @Test
    @DisplayName("refresh token updates revoke one token, or all of a user's but one")
    void refreshTokenUpdates() {
        String user = "user-" + run();
        Instant expires = Instant.now().plus(Duration.ofDays(30));
        RefreshToken a = refreshTokens.save(new RefreshToken(null, user, "hash-a-" + user, expires, false, null, null));
        RefreshToken b = refreshTokens.save(new RefreshToken(null, user, "hash-b-" + user, expires, false, null, null));
        RefreshToken c = refreshTokens.save(new RefreshToken(null, user, "hash-c-" + user, expires, false, null, null));

        Instant rotated = Instant.parse("2026-10-22T12:00:00Z");
        refreshTokens.markRotated(a.getId(), rotated);
        assertThat(refreshTokens.findById(a.getId()).orElseThrow())
                .returns(true, RefreshToken::isRevoked).returns(rotated, RefreshToken::getRotatedAt);

        refreshTokens.revoke(a.getId());
        assertThat(refreshTokens.findById(a.getId()).orElseThrow().getRotatedAt()).isNull();

        refreshTokens.revokeAllForUserExcept(user, c.getId());
        assertThat(refreshTokens.findByTokenHash(b.getTokenHash()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokens.findByTokenHash(c.getTokenHash()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("clearing a club's default venue unsets every venue of that club only")
    void clearDefaultVenue() {
        String club = "club-" + run();
        Venue mine = venues.save(new Venue(null, club, "Mine", null, null, null, null, List.of(), null, null, null, true, null));
        Venue other = venues.save(new Venue(null, club + "-other", "Other", null, null, null, null, List.of(), null, null, null, true, null));

        venues.clearDefaultForClub(club);

        assertThat(venues.findById(mine.getId()).orElseThrow().isDefault()).isFalse();
        assertThat(venues.findById(other.getId()).orElseThrow().isDefault()).isTrue();
    }

    @Test
    @DisplayName("notifications list newest first up to the limit, and unread ones are counted")
    void notificationsListAndCount() {
        String user = "user-" + run();
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            notifications.save(new Notification(null, user, NotificationType.REQUEST_RECEIVED, "t" + i, "b",
                    null, null, null, null, i == 0, now.minusSeconds(10 - i)));
        }
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(user, Limit.of(2)))
                .extracting(Notification::getTitle).containsExactly("t2", "t1");
        assertThat(notifications.countByUserIdAndReadFalse(user)).isEqualTo(2);

        notifications.deleteByUserId(user);
        assertThat(notifications.countByUserIdAndReadFalse(user)).isZero();
    }

    @Test
    @DisplayName("invites are stored and found by a normalised email")
    void inviteEmailsAreNormalised() {
        String email = "Invitee." + run() + "@Example.com";
        String team = "team-" + run();
        Instant now = Instant.now();
        MemberInvite invite = invites.save(new MemberInvite(null, "  " + email + " ", team, Role.USER, "inviter",
                now, now.plus(MemberInvite.VALIDITY)));

        assertThat(invite.getEmail()).isEqualTo(email.toLowerCase());
        assertThat(invites.findByTeamIdAndEmail(team, email.toUpperCase())).isPresent();
        assertThat(invites.findPendingByEmail(email, now)).hasSize(1);
        assertThat(invites.findPendingByTeamId(team, now.plus(Duration.ofDays(31)))).isEmpty();

        invites.deleteByEmail(email.toUpperCase());
        assertThat(invites.findById(invite.getId())).isEmpty();
    }

    @Test
    @DisplayName("bookable availability includes both end dates and leaves out booked slots")
    void bookableAvailability() {
        String team = "team-" + run();
        LocalDate from = LocalDate.of(2027, 3, 1);
        LocalDate to = LocalDate.of(2027, 3, 7);
        for (LocalDate date : List.of(from.minusDays(1), from, to, to.plusDays(1))) {
            availability.save(new AvailabilitySlot(null, team, date, LocalTime.of(10, 0), LocalTime.of(12, 0),
                    HomeAwayPreference.EITHER, null, null, null, AvailabilityStatus.AVAILABLE));
        }
        availability.save(new AvailabilitySlot(null, team, from, LocalTime.of(14, 0), LocalTime.of(16, 0),
                HomeAwayPreference.EITHER, null, null, null, AvailabilityStatus.BOOKED));

        assertThat(availability.findBookableForTeamBetween(team, from, to))
                .extracting(AvailabilitySlot::getDate).containsExactlyInAnyOrder(from, to);
        assertThat(availability.findBookableForTeamsBetween(List.of(team, "nobody"), from, to)).hasSize(2);
        assertThat(availability.findByTeamIdAndDate(team, from)).hasSize(2);
    }

    @Test
    @DisplayName("a conversation is found from either team's side")
    void conversationBetweenTeams() {
        String a = "team-a-" + run();
        String b = "team-b-" + run();
        Instant now = Instant.now();
        Conversation saved = conversations.save(new Conversation(null, a, b, now, now, null, null, null));

        assertThat(conversations.findBetweenTeams(b, a)).get().extracting(Conversation::getId).isEqualTo(saved.getId());
        assertThat(conversations.findByTeamId(b)).extracting(Conversation::getId).contains(saved.getId());
    }

    @Test
    @DisplayName("open reports list safeguarding first, then newest first")
    void reportQueueOrder() {
        String run = run();
        Instant now = Instant.now();
        reports.save(new Report(null, "r-" + run, "x", null, ReportType.SPAM, ReportSeverity.LOW, "older",
                ReportStatus.ESCALATED, null, List.of(), now.minusSeconds(30), null));
        reports.save(new Report(null, "r-" + run, "x", null, ReportType.SAFEGUARDING, ReportSeverity.SAFEGUARDING, "safeguarding",
                ReportStatus.ESCALATED, null, List.of(), now.minusSeconds(60), null));
        reports.save(new Report(null, "r-" + run, "x", null, ReportType.SPAM, ReportSeverity.LOW, "newer",
                ReportStatus.ESCALATED, null, List.of(), now, null));

        List<String> details = reports.findByStatus(ReportStatus.ESCALATED).stream()
                .filter(r -> r.getReporterTeamId().equals("r-" + run)).map(Report::getDetails).toList();
        assertThat(details).containsExactly("safeguarding", "newer", "older");
    }

    @Test
    @DisplayName("a push token is only removed for the user who holds it")
    void pushTokenOwnership() {
        String token = "token-" + run();
        pushTokens.save(new PushToken(token, "owner", PushPlatform.ANDROID, Instant.now()));

        pushTokens.deleteByTokenAndUserId(token, "someone-else");
        assertThat(pushTokens.findById(token)).isPresent();

        pushTokens.deleteByTokenAndUserId(token, "owner");
        assertThat(pushTokens.findById(token)).isEmpty();
    }

    private static org.assertj.core.data.TemporalUnitOffset within(Duration duration) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(duration.toSeconds(), java.time.temporal.ChronoUnit.SECONDS);
    }
}
