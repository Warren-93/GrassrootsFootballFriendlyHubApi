package com.gffh.api.service;

import com.gffh.api.domain.VerificationTokenPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Delivers verification and password-reset links over SMTP.
 *
 * <p>Any SMTP provider works: Spring Boot only creates a {@link JavaMailSender}
 * when {@code SPRING_MAIL_HOST} is set, and {@code MAIL_FROM} names the sender.
 * Until both are set this is "not configured", and {@link VerificationTokenService}
 * keeps its development behaviour of logging the token and handing it back to
 * the client instead.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String appBaseUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${gffh.mail.from:}") String from,
                        @Value("${gffh.mail.app-base-url:}") String appBaseUrl) {
        this.mailSender = mailSender.getIfAvailable();
        this.from = from;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    public boolean isConfigured() {
        return mailSender != null && !from.isBlank();
    }

    /**
     * Sends the link for {@code purpose}. Failures are logged, not thrown: a
     * flaky mail server shouldn't fail the registration or reset request that
     * triggered it, and the user can ask for another link.
     */
    public void sendToken(String email, VerificationTokenPurpose purpose, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        String link = linkFor(purpose, rawToken);
        if (purpose == VerificationTokenPurpose.PASSWORD_RESET) {
            message.setSubject("Reset your Grassroots Football Friendly Hub password");
            message.setText("""
                    Someone asked to reset the password for this account. If it was you, follow this link within 30 minutes:

                    %s

                    If you didn't ask for this, you can ignore this email - your password hasn't changed.
                    """.formatted(link));
        } else {
            message.setSubject("Verify your email for Grassroots Football Friendly Hub");
            message.setText("""
                    Confirm this is your email address by following this link within 30 minutes:

                    %s

                    If you didn't create an account, you can ignore this email.
                    """.formatted(link));
        }
        try {
            mailSender.send(message);
            log.info("Sent {} email", purpose);
        } catch (MailException e) {
            log.warn("Could not send {} email: {}", purpose, e.getMessage());
        }
    }

    String linkFor(VerificationTokenPurpose purpose, String rawToken) {
        String path = purpose == VerificationTokenPurpose.PASSWORD_RESET ? "/reset-password" : "/verify-email";
        return appBaseUrl + path + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
