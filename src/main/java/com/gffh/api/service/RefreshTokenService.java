package com.gffh.api.service;

import com.gffh.api.model.RefreshToken;
import com.gffh.api.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and rotates opaque refresh tokens. Opaque rather than JWT: a refresh
 * token is long-lived and revocable, which needs a database row to revoke -
 * a self-contained JWT cannot be un-issued short of a deny-list. Only the
 * SHA-256 digest is persisted (see {@link com.gffh.api.model.RefreshToken}).
 */
@Service
public class RefreshTokenService {

    private static final long TTL_DAYS = 30;
    /** How long a just-replaced token still works - see {@link RefreshToken#isRecentlyRotated}. */
    static final Duration ROTATION_GRACE = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenService(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    /**
     * A newly issued refresh token and the id of its stored row. Access tokens
     * issued alongside it carry that id, so a request can say which session
     * it came from (see {@link #revokeAllExcept}).
     */
    public record Issued(String id, String token) {}

    public Issued issue(String userId) {
        String rawToken = randomToken();
        RefreshToken saved = refreshTokens.save(new RefreshToken(null, userId, hash(rawToken),
                Instant.now().plus(TTL_DAYS, ChronoUnit.DAYS), false, Instant.now(), null));
        return new Issued(saved.getId(), rawToken);
    }

    /**
     * Validates and rotates: the presented token is revoked and a new one
     * issued in the same call, so a stolen-and-replayed refresh token is
     * usable at most once before the legitimate client's next refresh fails
     * loudly rather than silently racing an attacker.
     *
     * <p>A token replaced within the last {@link #ROTATION_GRACE} is still
     * accepted, so a reload mid-refresh or two tabs opening at once don't sign
     * the user out. Signing out or a password change ends that grace at once.
     */
    public Optional<Issued> rotate(String userId, String presentedToken) {
        Instant now = Instant.now();
        Optional<RefreshToken> found = accepted(presentedToken, now).filter(t -> t.getUserId().equals(userId));
        if (found.isEmpty()) return Optional.empty();

        if (found.get().isUsable(now)) refreshTokens.markRotated(found.get().getId(), now);
        return Optional.of(issue(userId));
    }

    private Optional<RefreshToken> accepted(String presentedToken, Instant now) {
        return refreshTokens.findByTokenHash(hash(presentedToken))
                .filter(t -> t.isUsable(now) || t.isRecentlyRotated(now, ROTATION_GRACE));
    }

    /** Sign-out: the presented token can't be used again. Unknown tokens are ignored. */
    public void revoke(String presentedToken) {
        refreshTokens.findByTokenHash(hash(presentedToken)).ifPresent(t -> refreshTokens.revoke(t.getId()));
    }

    /** Signs the user out everywhere: none of their refresh tokens can be used again. */
    public void revokeAll(String userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    /**
     * Signs the user out everywhere except the session {@code keepTokenId}
     * (an access token's session claim). With no session to keep, signs them
     * out everywhere.
     */
    public void revokeAllExcept(String userId, String keepTokenId) {
        if (keepTokenId == null) {
            revokeAll(userId);
        } else {
            refreshTokens.revokeAllForUserExcept(userId, keepTokenId);
        }
    }

    public Optional<String> userIdFor(String presentedToken) {
        return accepted(presentedToken, Instant.now()).map(RefreshToken::getUserId);
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
