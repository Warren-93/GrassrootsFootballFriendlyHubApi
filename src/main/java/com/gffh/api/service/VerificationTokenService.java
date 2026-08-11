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
 * <p>No email provider is configured (see the Screen Build Specification's
 * SCR-AU-05/06 flows, which assume delivery). Rather than fake success
 * silently, the token is logged at INFO so it can be exercised end-to-end in
 * development; wiring a real provider replaces only {@link #issue} callers'
 * next step, not this class's contract.
 */
@Service
public class VerificationTokenService {

    private static final Logger log = LoggerFactory.getLogger(VerificationTokenService.class);
    private static final long TTL_MINUTES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationTokenRepository tokens;

    public VerificationTokenService(VerificationTokenRepository tokens) {
        this.tokens = tokens;
    }

    public void issue(String userId, VerificationTokenPurpose purpose, String email) {
        String rawToken = randomToken();
        tokens.save(new VerificationToken(null, userId, rawToken, purpose,
                Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES), false, Instant.now()));
        log.info("Verification token issued [purpose={}, email={}, token={}] "
                + "- no email provider configured, logging in place of delivery", purpose, email, rawToken);
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
