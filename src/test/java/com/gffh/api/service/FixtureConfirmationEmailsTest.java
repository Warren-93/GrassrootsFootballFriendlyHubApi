package com.gffh.api.service;

import com.gffh.api.domain.*;
import com.gffh.api.repository.ClubRepository;
import com.gffh.api.repository.NotificationPreferenceRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FixtureConfirmationEmailsTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final TeamRepository teams = mock(TeamRepository.class);
    private final ClubRepository clubs = mock(ClubRepository.class);
    private final VenueRepository venues = mock(VenueRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);
    private final MembershipService memberships = mock(MembershipService.class);

    private final Fixture fixture = new Fixture("fx-1", "fr-1", "home", "away", LocalDate.of(2026, 10, 24),
            LocalTime.of(10, 0), LocalTime.of(12, 0), "venue-1", FixtureStatus.CONFIRMED,
            CostShare.SPLIT, null, Instant.now());

    @BeforeEach
    void setUp() {
        when(teams.findById("home")).thenReturn(Optional.of(team("home", "club-h", "Rovers U12", "Sam Home")));
        when(teams.findById("away")).thenReturn(Optional.of(team("away", "club-a", "United U12", "Alex Away")));
        when(clubs.findById("club-h")).thenReturn(Optional.of(club("club-h", "secretary@rovers.example")));
        when(clubs.findById("club-a")).thenReturn(Optional.of(club("club-a", null)));
        when(venues.findById("venue-1")).thenReturn(Optional.of(new Venue("venue-1", "club-h", "Rovers Park",
                "1 Pitch Lane", "FK2 7ZX", new GeoPoint(-3.790261, 56.014582), null, List.of(), null, null, null,
                true, Instant.now())));
        when(memberships.managerUserIdsFor("home")).thenReturn(List.of("u-home"));
        when(memberships.managerUserIdsFor("away")).thenReturn(List.of("u-away", "u-muted", "u-unverified"));
        when(users.findById("u-home")).thenReturn(Optional.of(user("u-home", "Home@Example.com", true)));
        when(users.findById("u-away")).thenReturn(Optional.of(user("u-away", "away@example.com", true)));
        when(users.findById("u-muted")).thenReturn(Optional.of(user("u-muted", "muted@example.com", true)));
        when(users.findById("u-unverified")).thenReturn(Optional.of(user("u-unverified", "new@example.com", false)));
        when(preferences.findByUserId(anyString())).thenReturn(Optional.empty());
        when(preferences.findByUserId("u-muted"))
                .thenReturn(Optional.of(new NotificationPreference("u-muted", true, false, true, true)));
    }

    @Test
    void emailsBothTeamsManagersAndClubContact() {
        emails(noAssociations(), "noreply@example.com").sendFor(fixture);

        List<SimpleMailMessage> sent = sentMessages();
        assertEquals(List.of("home@example.com", "secretary@rovers.example", "away@example.com"),
                sent.stream().map(m -> m.getTo()[0]).toList());
        SimpleMailMessage first = sent.get(0);
        assertEquals("Fixture confirmed: Rovers U12 v United U12, Sat 24 Oct", first.getSubject());
        assertTrue(first.getText().contains("Saturday 24 October 2026"));
        assertTrue(first.getText().contains("10:00 to 12:00"));
        assertTrue(first.getText().contains("Venue: Rovers Park, 1 Pitch Lane, FK2 7ZX"));
        assertTrue(first.getText().contains(
                "Directions: https://www.google.com/maps/dir/?api=1&destination=56.014582,-3.790261"));
        assertTrue(first.getText().contains("https://app.example.com/fixtures/fx-1"));
    }

    @Test
    void aPostcodeAlreadyInTheAddressIsNotRepeatedAndDirectionsFallBackToIt() {
        Venue typedInAddress = new Venue("v", "c", "Rec Ground", "Mill Lane, Falkirk fk2 7zx", null, null,
                null, List.of(), null, null, null, true, Instant.now());

        assertEquals("Venue: Rec Ground, Mill Lane, Falkirk fk2 7zx\n"
                        + "Directions: https://www.google.com/maps/dir/?api=1&destination=FK2+7ZX",
                FixtureConfirmationEmails.venueLines(typedInAddress));
    }

    @Test
    void aFixtureWithNoVenueSaysItIsToBeArranged() {
        Fixture noVenue = new Fixture("fx-2", "fr-2", "home", "away", LocalDate.of(2026, 10, 24),
                LocalTime.of(10, 0), null, null, FixtureStatus.CONFIRMED, CostShare.SPLIT, null, Instant.now());

        String body = emails(noAssociations(), "noreply@example.com")
                .body(noVenue, teams.findById("home").orElseThrow(), teams.findById("away").orElseThrow());

        assertTrue(body.contains("Venue: To be arranged between the teams"));
        assertFalse(body.contains("Directions:"));
    }

    @Test
    void associationRecipientsAreCopiedInWhenProvided() {
        AssociationRecipients fa = team -> List.of("fixtures@" + team.id() + "-fa.example");

        emails(providerOf(fa), "noreply@example.com").sendFor(fixture);

        List<String> to = sentMessages().stream().map(m -> m.getTo()[0]).toList();
        assertTrue(to.contains("fixtures@home-fa.example"));
        assertTrue(to.contains("fixtures@away-fa.example"));
    }

    @Test
    void nothingIsSentWhenEmailIsNotConfigured() {
        emails(noAssociations(), "").sendFor(fixture);

        verifyNoInteractions(mailSender, teams);
    }

    @Test
    void aMailServerFailureDoesNotThrow() {
        doThrow(new MailSendException("down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emails(noAssociations(), "noreply@example.com").sendFor(fixture));
    }

    private List<SimpleMailMessage> sentMessages() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private FixtureConfirmationEmails emails(ObjectProvider<AssociationRecipients> associations, String from) {
        ObjectProvider<JavaMailSender> senderProvider = mock(ObjectProvider.class);
        when(senderProvider.getIfAvailable()).thenReturn(mailSender);
        EmailService email = new EmailService(senderProvider, from, "https://app.example.com");
        return new FixtureConfirmationEmails(email, teams, clubs, venues, users, preferences, memberships,
                associations, Runnable::run);
    }

    private static ObjectProvider<AssociationRecipients> noAssociations() {
        return providerOf();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AssociationRecipients> providerOf(AssociationRecipients... beans) {
        ObjectProvider<AssociationRecipients> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenAnswer(i -> Stream.of(beans));
        return provider;
    }

    private static Team team(String id, String clubId, String name, String manager) {
        return new Team(id, clubId, name, null, null, null, null, null, null, null, null, null, 10, null,
                manager, null, null, null, null, null, Instant.now(), Instant.now(), false, false, true, false);
    }

    private static Club club(String id, String contactEmail) {
        return new Club(id, "Club " + id, null, null, null, null, contactEmail, Instant.now());
    }

    private static User user(String id, String email, boolean verified) {
        return new User(id, email, "hash", id, verified, Instant.now(), false);
    }
}
