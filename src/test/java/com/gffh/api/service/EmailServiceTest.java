package com.gffh.api.service;

import com.gffh.api.domain.VerificationTokenPurpose;
import com.gffh.api.repository.VerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final VerificationTokenRepository tokens = mock(VerificationTokenRepository.class);

    @Test
    void emailsAVerifyLinkAndKeepsTheTokenOutOfTheResponse() {
        EmailService email = emailService(mailSender, "noreply@example.com", "https://app.example.com/");
        VerificationTokenService service = new VerificationTokenService(tokens, email);

        String token = service.issue("user-1", VerificationTokenPurpose.EMAIL_VERIFY, "manager@example.com");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertArrayEquals(new String[] {"manager@example.com"}, sent.getValue().getTo());
        assertEquals("noreply@example.com", sent.getValue().getFrom());
        assertTrue(sent.getValue().getText().contains("https://app.example.com/verify-email?token=" + token));
        assertNull(service.forResponse(token));
    }

    @Test
    void passwordResetLinksGoToTheResetPage() {
        EmailService email = emailService(mailSender, "noreply@example.com", "https://app.example.com");

        assertEquals("https://app.example.com/reset-password?token=a%2Bb",
                email.linkFor(VerificationTokenPurpose.PASSWORD_RESET, "a+b"));
    }

    @Test
    void withoutAProviderTheTokenIsReturnedAndNothingIsSent() {
        VerificationTokenService service = new VerificationTokenService(tokens, emailService(null, "", "https://app.example.com"));

        String token = service.issue("user-1", VerificationTokenPurpose.EMAIL_VERIFY, "manager@example.com");

        assertEquals(token, service.forResponse(token));
        verifyNoInteractions(mailSender);
    }

    @Test
    void aSenderWithNoFromAddressIsNotConfigured() {
        assertFalse(emailService(mailSender, "", "https://app.example.com").isConfigured());
    }

    @Test
    void aMailServerFailureDoesNotFailTheRequest() {
        doThrow(new MailSendException("down")).when(mailSender).send(any(SimpleMailMessage.class));
        VerificationTokenService service = new VerificationTokenService(tokens,
                emailService(mailSender, "noreply@example.com", "https://app.example.com"));

        assertDoesNotThrow(() -> service.issue("user-1", VerificationTokenPurpose.PASSWORD_RESET, "manager@example.com"));
    }

    @SuppressWarnings("unchecked")
    private static EmailService emailService(JavaMailSender sender, String from, String baseUrl) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new EmailService(provider, from, baseUrl);
    }
}
