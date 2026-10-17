package com.gffh.api.service;

import com.gffh.api.domain.VerificationTokenPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Delivers verification and password-reset links, and fixture confirmations,
 * over SMTP - or over Brevo's HTTPS API when {@code BREVO_API_KEY} is set,
 * for hosts that block SMTP ports (see {@link BrevoMailer}).
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
    private final BrevoMailer brevo;
    private final String from;
    private final String appBaseUrl;

    @Autowired
    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        ObjectProvider<BrevoMailer> brevo,
                        @Value("${gffh.mail.from:}") String from,
                        @Value("${gffh.mail.app-base-url:}") String appBaseUrl) {
        BrevoMailer api = brevo.getIfAvailable();
        this.brevo = api != null && api.isConfigured() ? api : null;
        this.mailSender = mailSender.getIfAvailable();
        this.from = from;
        this.appBaseUrl = appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    /** SMTP only, as before Brevo was an option. */
    public EmailService(ObjectProvider<JavaMailSender> mailSender, String from, String appBaseUrl) {
        this(mailSender, new NoBrevo(), from, appBaseUrl);
    }

    public boolean isConfigured() {
        return (brevo != null || mailSender != null) && !from.isBlank();
    }

    /**
     * Sends the link for {@code purpose}. Failures are logged, not thrown: a
     * flaky mail server shouldn't fail the registration or reset request that
     * triggered it, and the user can ask for another link.
     */
    public void sendToken(String email, VerificationTokenPurpose purpose, String rawToken) {
        String link = linkFor(purpose, rawToken);
        String subject;
        String text;
        if (purpose == VerificationTokenPurpose.PASSWORD_RESET) {
            subject = "Reset your Kickabout password";
            text = """
                    Someone asked to reset the password for this account. If it was you, follow this link within 30 minutes:

                    %s

                    If you didn't ask for this, you can ignore this email - your password hasn't changed.
                    """.formatted(link);
        } else {
            subject = "Verify your email for Kickabout";
            text = """
                    Confirm this is your email address by following this link within 30 minutes:

                    %s

                    If you didn't create an account, you can ignore this email.
                    """.formatted(link);
        }
        if (deliver(email, subject, text)) log.info("Sent {} email", purpose);
    }

    /**
     * Sends a plain-text email to one address. Like {@link #sendToken}, failures
     * are logged rather than thrown, and nothing is sent until the provider is
     * configured.
     */
    public void send(String to, String subject, String text) {
        if (!isConfigured()) {
            log.info("Email not configured; skipped \"{}\"", subject);
            return;
        }
        if (deliver(to, subject, text)) log.info("Sent \"{}\" email", subject);
    }

    /** Sends through Brevo when it's set up, else SMTP; logs and answers false on failure. */
    private boolean deliver(String to, String subject, String text) {
        try {
            if (brevo != null) {
                brevo.send(from, to, subject, text);
            } else {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(from);
                message.setTo(to);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
            }
            return true;
        } catch (MailException | IOException e) {
            log.warn("Could not send \"{}\" email: {}", subject, e.getMessage());
            return false;
        }
    }

    /** Stands in for a missing BrevoMailer in the SMTP-only constructor. */
    private static final class NoBrevo implements ObjectProvider<BrevoMailer> {
        @Override public BrevoMailer getObject(Object... args) { return null; }
        @Override public BrevoMailer getIfAvailable() { return null; }
        @Override public BrevoMailer getIfUnique() { return null; }
        @Override public BrevoMailer getObject() { return null; }
    }

    /** An absolute link into the web app, e.g. {@code /fixtures/123}. */
    public String appLink(String path) {
        return appBaseUrl + path;
    }

    String linkFor(VerificationTokenPurpose purpose, String rawToken) {
        String path = purpose == VerificationTokenPurpose.PASSWORD_RESET ? "/reset-password" : "/verify-email";
        return appBaseUrl + path + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }
}
