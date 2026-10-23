package com.gffh.api.service.email;

import com.gffh.api.model.Club;
import com.gffh.api.model.Fixture;
import com.gffh.api.model.NotificationPreference;
import com.gffh.api.model.Team;
import com.gffh.api.model.User;
import com.gffh.api.model.Venue;
import com.gffh.api.model.enums.NotificationCategory;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.NotificationPreferenceRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.repository.VenueRepository;
import com.gffh.api.service.MembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Emails a fixture confirmation to both teams once a friendly is accepted.
 *
 * <p>Each team's contacts are its managers (verified emails, unless they've
 * turned fixture notifications or emails off) plus its club's contact email.
 * Anything an {@link AssociationRecipients} bean returns is added too, which
 * is how associations will be copied in later.
 *
 * <p>Sending happens off the request thread: an SMTP server can take seconds to
 * answer, and accepting a friendly must never wait on, or fail because of, email.
 */
@Service
public class FixtureConfirmationEmails {

    private static final Logger log = LoggerFactory.getLogger(FixtureConfirmationEmails.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final EmailService email;
    private final TeamRepository teams;
    private final ClubRepository clubs;
    private final VenueRepository venues;
    private final UserRepository users;
    private final NotificationPreferenceRepository preferences;
    private final MembershipService memberships;
    private final ObjectProvider<AssociationRecipients> associations;
    private final Executor executor;

    @Autowired
    public FixtureConfirmationEmails(EmailService email, TeamRepository teams, ClubRepository clubs,
                                     VenueRepository venues, UserRepository users,
                                     NotificationPreferenceRepository preferences, MembershipService memberships,
                                     ObjectProvider<AssociationRecipients> associations) {
        this(email, teams, clubs, venues, users, preferences, memberships, associations,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    FixtureConfirmationEmails(EmailService email, TeamRepository teams, ClubRepository clubs,
                              VenueRepository venues, UserRepository users,
                              NotificationPreferenceRepository preferences, MembershipService memberships,
                              ObjectProvider<AssociationRecipients> associations, Executor executor) {
        this.email = email;
        this.teams = teams;
        this.clubs = clubs;
        this.venues = venues;
        this.users = users;
        this.preferences = preferences;
        this.memberships = memberships;
        this.associations = associations;
        this.executor = executor;
    }

    /** Queues the confirmation for {@code fixture}. Never throws. */
    public void sendFor(Fixture fixture) {
        if (!email.isConfigured()) {
            log.info("Email not configured; no confirmation email for fixture {}", fixture.getId());
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    send(fixture);
                } catch (Exception e) {
                    log.warn("Could not send confirmation emails for fixture {}", fixture.getId(), e);
                }
            });
        } catch (Exception e) {
            log.warn("Could not queue confirmation emails for fixture {}", fixture.getId(), e);
        }
    }

    void send(Fixture fixture) {
        Team home = teams.findById(fixture.getHomeTeamId()).orElse(null);
        Team away = teams.findById(fixture.getAwayTeamId()).orElse(null);
        if (home == null || away == null) return;

        Set<String> recipients = new LinkedHashSet<>();
        for (Team team : List.of(home, away)) {
            recipients.addAll(contactsFor(team));
            associations.orderedStream().forEach(a -> a.emailsFor(team).forEach(e -> addIfPresent(recipients, e)));
        }

        String subject = "Fixture confirmed: %s v %s, %s".formatted(
                displayName(home), displayName(away), fixture.getDate().format(SHORT_DATE));
        String body = body(fixture, home, away);
        for (String to : recipients) {
            email.send(to, subject, body);
        }
    }

    /**
     * A team's contact addresses: its managers who want fixture updates by
     * email, then its club's contact email.
     */
    Set<String> contactsFor(Team team) {
        Set<String> out = new LinkedHashSet<>();
        for (String userId : memberships.managerUserIdsFor(team.getId())) {
            NotificationPreference preference = preferences.findByUserId(userId)
                    .orElseGet(() -> NotificationPreference.defaultsFor(userId));
            if (!preference.allows(NotificationCategory.FIXTURES) || !preference.isEmailEnabled()) continue;
            users.findById(userId)
                    .filter(u -> u.isEmailVerified() && !u.isSuspended())
                    .map(User::getEmail)
                    .ifPresent(e -> addIfPresent(out, e));
        }
        Optional.ofNullable(team.getClubId()).flatMap(clubs::findById)
                .map(Club::getContactEmail)
                .ifPresent(e -> addIfPresent(out, e));
        return out;
    }

    String body(Fixture fixture, Team home, Team away) {
        String venue = Optional.ofNullable(fixture.getVenueId()).flatMap(venues::findById)
                .map(FixtureConfirmationEmails::venueLines)
                .orElse("Venue: To be arranged between the teams");
        String time = fixture.getStartTime().format(TIME)
                + (fixture.getEndTime() != null ? " to " + fixture.getEndTime().format(TIME) : "");

        return """
                Your friendly is confirmed.

                %s (home) v %s (away)
                Date: %s
                Time: %s
                %s

                Home manager: %s
                Away manager: %s

                See the fixture, contact details and directions on Kickabout:
                %s

                You're getting this because you're a contact for one of the teams. Team managers can turn off fixture emails in their notification settings.
                """.formatted(
                displayName(home), displayName(away),
                fixture.getDate().format(DATE), time, venue,
                orDash(home.getManagerName()), orDash(away.getManagerName()),
                email.appLink("/fixtures/" + fixture.getId()));
    }

    /**
     * "Venue: name, address, postcode" (the postcode only when the address
     * doesn't already include it), then a Google Maps directions line.
     */
    static String venueLines(Venue v) {
        StringBuilder line = new StringBuilder("Venue: ").append(v.getName());
        if (v.getAddress() != null && !v.getAddress().isBlank()) line.append(", ").append(v.getAddress().strip());
        String postcode = v.displayPostcode();
        if (postcode != null && !squashed(v.getAddress()).contains(squashed(postcode))) {
            line.append(", ").append(postcode);
        }
        String directions = v.directionsUrl();
        if (directions != null) line.append("\nDirections: ").append(directions);
        return line.toString();
    }

    private static String squashed(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String displayName(Team team) {
        return team.getName();
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static void addIfPresent(Set<String> set, String email) {
        if (email != null && !email.isBlank()) set.add(email.trim().toLowerCase(Locale.ROOT));
    }
}
