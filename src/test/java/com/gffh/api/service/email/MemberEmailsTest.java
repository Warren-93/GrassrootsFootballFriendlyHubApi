package com.gffh.api.service.email;

import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.Role;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberEmailsTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final Team team = new Team("team-1", "club-1", "Rovers U12", null, null, null, null, null, null, null,
            null, null, 10, null, null, null, null, null, null, null, Instant.now(), Instant.now(), false, false,
            true, false);

    @Test
    void anInvitationLinksToRegisterWithTheAddressFilledIn() {
        MemberInvite invite = new MemberInvite("inv-1", "new+player@example.com", "team-1", Role.TEAM_MANAGER,
                "u-1", Instant.parse("2026-10-15T12:00:00Z"), Instant.parse("2026-11-14T12:00:00Z"));

        emails("noreply@example.com").sendInvite(invite, "Sam Smith", team);

        SimpleMailMessage mail = sent();
        assertArrayEquals(new String[] {"new+player@example.com"}, mail.getTo());
        assertEquals("Sam Smith invited you to join Rovers U12 on Kickabout", mail.getSubject());
        assertTrue(mail.getText().contains("join Rovers U12 on Kickabout as a team manager."));
        assertTrue(mail.getText().contains("https://app.example.com/register?email=new%2Bplayer%40example.com"));
        assertTrue(mail.getText().contains("The invitation expires on 14 November 2026."));
    }

    @Test
    void anExistingAccountIsToldTheyHaveBeenAdded() {
        emails("noreply@example.com").sendAdded("player@example.com", "Sam Smith", team, Role.USER);

        SimpleMailMessage mail = sent();
        assertEquals("You've been added to Rovers U12", mail.getSubject());
        assertTrue(mail.getText().contains("Sam Smith added you to Rovers U12 on Kickabout as a member."));
        assertTrue(mail.getText().contains("https://app.example.com/team/team-1"));
    }

    @Test
    void nothingIsSentWhenEmailIsNotConfigured() {
        emails("").sendAdded("player@example.com", "Sam Smith", team, Role.USER);

        verifyNoInteractions(mailSender);
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private MemberEmails emails(String from) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mailSender);
        return new MemberEmails(new EmailService(provider, from, "https://app.example.com"), Runnable::run);
    }
}
