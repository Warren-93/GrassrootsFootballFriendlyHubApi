package com.gffh.api.service;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import com.gffh.api.repository.NotificationPreferenceRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.service.email.EmailService;
import com.gffh.api.service.push.PushMessage;
import com.gffh.api.service.push.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationDeliveryTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final PushService push = mock(PushService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TeamRepository teams = mock(TeamRepository.class);
    private final NotificationPreferenceRepository preferences = mock(NotificationPreferenceRepository.class);

    @BeforeEach
    void setUp() {
        when(push.isConfigured()).thenReturn(true);
        when(users.findById("u1")).thenReturn(Optional.of(user(true, false)));
        when(teams.findById("team-1")).thenReturn(Optional.of(team()));
        when(preferences.findByUserId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void emailsAndPushesARequestToTheManager() {
        delivery("noreply@example.com").deliver(notification(NotificationType.REQUEST_RECEIVED, "fr-1", null, null));

        SimpleMailMessage mail = sentMail();
        assertArrayEquals(new String[] {"manager@example.com"}, mail.getTo());
        assertEquals("New friendly request", mail.getSubject());
        assertTrue(mail.getText().startsWith("New friendly request\n\nRovers sent you a friendly request.\n"));
        assertTrue(mail.getText().contains("https://app.example.com/request/fr-1"));
        assertTrue(mail.getText().contains("you manage United U12 on Kickabout"));

        PushMessage pushed = pushedMessage();
        assertEquals("New friendly request", pushed.title());
        assertEquals("Rovers sent you a friendly request.", pushed.body());
        assertEquals(Map.of("type", "REQUEST_RECEIVED", "requestId", "fr-1", "teamId", "team-1"), pushed.data());
    }

    @Test
    void chatAndFixtureConfirmationsArePushedButNotEmailed() {
        NotificationDelivery delivery = delivery("noreply@example.com");

        delivery.deliver(notification(NotificationType.MESSAGE_RECEIVED, null, null, "conv-1"));
        delivery.deliver(notification(NotificationType.FIXTURE_CONFIRMED, "fr-1", "fx-1", null));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(push, times(2)).sendToUser(eq("u1"), any());
    }

    @Test
    void eachChannelFollowsItsOwnSwitch() {
        when(preferences.findByUserId("u1"))
                .thenReturn(Optional.of(new NotificationPreference("u1", true, true, true, true, false, true)));
        delivery("noreply@example.com").deliver(notification(NotificationType.REQUEST_DECLINED, "fr-1", null, null));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        verify(push).sendToUser(eq("u1"), any());

        reset(push);
        when(push.isConfigured()).thenReturn(true);
        when(preferences.findByUserId("u1"))
                .thenReturn(Optional.of(new NotificationPreference("u1", true, true, true, true, true, false)));
        delivery("noreply@example.com").deliver(notification(NotificationType.REQUEST_DECLINED, "fr-1", null, null));
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(push, never()).sendToUser(anyString(), any());
    }

    @Test
    void aMutedCategoryGoesNowhere() {
        when(preferences.findByUserId("u1"))
                .thenReturn(Optional.of(new NotificationPreference("u1", false, true, true, true, true, true)));

        delivery("noreply@example.com").deliver(notification(NotificationType.REQUEST_RECEIVED, "fr-1", null, null));

        verifyNoInteractions(mailSender);
        verify(push, never()).sendToUser(anyString(), any());
    }

    @Test
    void anUnverifiedAddressIsNotEmailedButStillGetsPushes() {
        when(users.findById("u1")).thenReturn(Optional.of(user(false, false)));

        delivery("noreply@example.com").deliver(notification(NotificationType.REQUEST_RECEIVED, "fr-1", null, null));

        verifyNoInteractions(mailSender);
        verify(push).sendToUser(eq("u1"), any());
    }

    @Test
    void aSuspendedAccountGetsNothing() {
        when(users.findById("u1")).thenReturn(Optional.of(user(true, true)));

        delivery("noreply@example.com").deliver(notification(NotificationType.REQUEST_RECEIVED, "fr-1", null, null));

        verifyNoInteractions(mailSender);
        verify(push, never()).sendToUser(anyString(), any());
    }

    @Test
    void nothingIsQueuedWhenNeitherChannelIsConfigured() {
        when(push.isConfigured()).thenReturn(false);
        Executor executor = mock(Executor.class);

        new NotificationDelivery(emailService(""), push, users, teams, preferences, executor)
                .deliver(notification(NotificationType.REQUEST_RECEIVED, "fr-1", null, null));

        verifyNoInteractions(executor, users);
    }

    @Test
    void aMailFailureNeverEscapes() {
        doThrow(new MailSendException("down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> delivery("noreply@example.com")
                .deliver(notification(NotificationType.REQUEST_RECEIVED, "fr-1", null, null)));
        verify(push).sendToUser(eq("u1"), any());
    }

    @Test
    void linksPointAtThePageTheNotificationIsAbout() {
        assertEquals("/messages/conv-1",
                NotificationDelivery.pathFor(notification(NotificationType.MESSAGE_RECEIVED, null, null, "conv-1")));
        assertEquals("/fixtures/fx-1",
                NotificationDelivery.pathFor(notification(NotificationType.FIXTURE_CONFIRMED, "fr-1", "fx-1", null)));
        assertEquals("/request/fr-1",
                NotificationDelivery.pathFor(notification(NotificationType.FIXTURE_CANCELLED, "fr-1", null, null)));
        assertEquals("/team/team-1/verification",
                NotificationDelivery.pathFor(notification(NotificationType.VERIFICATION_APPROVED, null, null, null)));
    }

    private SimpleMailMessage sentMail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private PushMessage pushedMessage() {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(push).sendToUser(eq("u1"), captor.capture());
        return captor.getValue();
    }

    private NotificationDelivery delivery(String from) {
        return new NotificationDelivery(emailService(from), push, users, teams, preferences, Runnable::run);
    }

    @SuppressWarnings("unchecked")
    private EmailService emailService(String from) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        return new EmailService(provider, from, "https://app.example.com");
    }

    private static Notification notification(NotificationType type, String requestId, String fixtureId,
                                             String conversationId) {
        String title = type == NotificationType.REQUEST_RECEIVED ? "New friendly request" : "Something happened";
        return new Notification("n-1", "u1", type, title, "Rovers sent you a friendly request.", "team-1",
                requestId, fixtureId, conversationId, false, Instant.now());
    }

    private static User user(boolean verified, boolean suspended) {
        return new User("u1", "manager@example.com", "hash", "Sam", verified, Instant.now(), suspended);
    }

    private static Team team() {
        return new Team("team-1", "club-1", "United U12", null, null, null, null, null, null, null, null, null, null, 10,
                null, null, null, null, null, null, null, Instant.now(), Instant.now(), false, false, true, false);
    }
}
