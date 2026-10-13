package com.gffh.api.service;

import com.gffh.api.domain.VerificationToken;
import com.gffh.api.domain.VerificationTokenPurpose;
import com.gffh.api.repository.VerificationTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues single-use tokens for email verification and password reset.
 *
 * <p>Tokens are emailed when {@link EmailService} is configured. Without
 * an email provider (local development, tests) the token is logged at INFO
 * instead, and {@link #forResponse} lets the API hand it straight back to the
 * client so the SCR-AU-05/06 flows can still be exercised end to end.
 */
@Service
public class VerificationTokenService {

    private static final Logger log = LoggerFactory.getLogger(VerificationTokenService.class);
    private static final long TTL_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationTokenRepository tokens;
    private final EmailService emailService;

    public VerificationTokenService(VerificationTokenRepository tokens, EmailService emailService) {
        this.tokens = tokens;
        this.emailService = emailService;
    }

    /** Emails the token when a provider is configured; returns it either way, see {@link #forResponse}. */
    public String issue(String userId, VerificationTokenPurpose purpose, String email) {
        String rawToken = randomToken();
        tokens.save(new VerificationToken(null, userId, rawToken, purpose,
                Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES), false, Instant.now()));
        if (emailService.isConfigured()) {
            emailService.sendToken(email, purpose, rawToken);
        } else {
            log.info("Verification token issued [purpose={}, email={}, token={}] "
                    + "- no email provider configured, logging in place of delivery", purpose, email, rawToken);
        }
        return rawToken;
    }

    /**
     * The token to put in an API response: only while no email provider is
     * configured. Once tokens are emailed, receiving the email is the proof
     * of ownership, so returning the token as well would defeat it.
     */
    public String forResponse(String rawToken) {
        return emailService.isConfigured() ? null : rawToken;
    }

    public boolean emailsTokens() {
        return emailService.isConfigured();
    }

    public Optional<String> consume(String rawToken, VerificationTokenPurpose purpose) {
        Optional<VerificationToken> found = tokens.findByToken(rawToken, purpose)
                .filter(t -> t.isUsable(Instant.now()));
        found.ifPresent(t -> tokens.markUsed(t.id()));
        return found.map(VerificationToken::userId);
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
