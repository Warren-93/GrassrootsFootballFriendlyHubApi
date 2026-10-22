package com.gffh.api.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Admin tokens reuse the same signing key as {@link JwtService} but carry a
 * "role" claim (mapped to a ROLE_PLATFORM_ADMIN authority by
 * SecurityConfig's JwtAuthenticationConverter) and a much shorter life.
 *
 * <p>ADM-01's 30-minute timeout is an idle timeout: each token lasts
 * {@code gffh.admin.session-minutes} (default 30) and the admin site swaps it
 * for a fresh one while the admin is using it ({@code POST /admin/auth/refresh}).
 * A "sessionStart" claim carries the original sign-in time through every
 * refresh, so a session still ends {@code gffh.admin.session-max-hours}
 * (default 12) after the password was typed, however active it stays.
 */
@Service
public class AdminJwtService {

    private static final String ISSUER = "gffh-api";
    private static final long PENDING_2FA_TTL_MINUTES = 5;
    static final String SESSION_START_CLAIM = "sessionStart";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final long sessionTtlMinutes;
    private final long sessionMaxHours;

    public AdminJwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder,
                           @Value("${gffh.admin.session-minutes:30}") long sessionTtlMinutes,
                           @Value("${gffh.admin.session-max-hours:12}") long sessionMaxHours) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.sessionTtlMinutes = sessionTtlMinutes;
        this.sessionMaxHours = sessionMaxHours;
    }

    /** Full session token for a fresh sign-in, issued only after the TOTP code (if on) is verified. */
    public String issueSessionToken(String adminId, String email) {
        return issueSessionToken(adminId, email, Instant.now());
    }

    /**
     * A token for an existing session that started at {@code sessionStart}.
     * Never outlives the session's maximum length.
     */
    public String issueSessionToken(String adminId, String email, Instant sessionStart) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionTtlMinutes, ChronoUnit.MINUTES);
        Instant sessionEnd = sessionEnd(sessionStart);
        if (expiresAt.isAfter(sessionEnd)) expiresAt = sessionEnd;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(adminId)
                .claim("email", email)
                .claim("role", "PLATFORM_ADMIN")
                .claim(SESSION_START_CLAIM, sessionStart.getEpochSecond())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /** Short-lived token identifying who passed step 1 (password), pending step 2 (TOTP). */
    public String issuePending2faToken(String adminId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(adminId)
                .claim("stage", "PENDING_2FA")
                .issuedAt(now)
                .expiresAt(now.plus(PENDING_2FA_TTL_MINUTES, ChronoUnit.MINUTES))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    /** @throws org.springframework.security.oauth2.jwt.JwtException if invalid, expired, or not a pending-2FA token. */
    public String verifyPending2faToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        if (!"PENDING_2FA".equals(jwt.getClaimAsString("stage"))) {
            throw new org.springframework.security.oauth2.jwt.BadJwtException("Not a pending-2FA token");
        }
        return jwt.getSubject();
    }

    /** When the session behind {@code token} began: its "sessionStart" claim, or its issue time for tokens from before that claim. */
    public Instant sessionStart(Jwt token) {
        Object claim = token.getClaims().get(SESSION_START_CLAIM);
        if (claim instanceof Number n) return Instant.ofEpochSecond(n.longValue());
        if (claim instanceof Instant i) return i;
        return token.getIssuedAt() != null ? token.getIssuedAt() : Instant.now();
    }

    /** The latest time a session that began at {@code sessionStart} may last until. */
    public Instant sessionEnd(Instant sessionStart) {
        return sessionStart.plus(sessionMaxHours, ChronoUnit.HOURS);
    }

    public long sessionTtlSeconds() {
        return sessionTtlMinutes * 60;
    }
}
