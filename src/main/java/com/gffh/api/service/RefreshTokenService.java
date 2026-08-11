package com.gffh.api.service;

import com.gffh.api.domain.RefreshToken;
import com.gffh.api.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and rotates opaque refresh tokens. Opaque rather than JWT: a refresh
 * token is long-lived and revocable, which needs a database row to revoke -
 * a self-contained JWT cannot be un-issued short of a deny-list. Only the
 * SHA-256 digest is persisted (see {@link com.gffh.api.domain.RefreshToken}).
 */
@Service
public class RefreshTokenService {

    private static final long TTL_DAYS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenService(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    public String issue(String userId) {
        String rawToken = randomToken();
        refreshTokens.save(new RefreshToken(null, userId, hash(rawToken),
                Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS), false, Instant.now()));
        return rawToken;
    }

    /**
     * Validates and rotates: the presented token is revoked and a new one
     * issued in the same call, so a stolen-and-replayed refresh token is
     * usable at most once before the legitimate client's next refresh fails
     * loudly rather than silently racing an attacker.
     */
    public Optional<String> rotate(String userId, String presentedToken) {
        Optional<RefreshToken> found = refreshTokens.findByTokenHash(hash(presentedToken))
                .filter(t -> t.userId().equals(userId))
                .filter(t -> t.isUsable(Instant.now()));
        if (found.isEmpty()) return Optional.empty();

        refreshTokens.revoke(found.get().id());
        return Optional.of(issue(userId));
    }

    public Optional<String> userIdFor(String presentedToken) {
        return refreshTokens.findByTokenHash(hash(presentedToken))
                .filter(t -> t.isUsable(Instant.now()))
                .map(RefreshToken::userId);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
