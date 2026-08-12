package com.gffh.api.service;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Admin tokens reuse the same signing key as {@link JwtService} but carry a
 * "role" claim (mapped to a ROLE_PLATFORM_ADMIN authority by
 * SecurityConfig's JwtAuthenticationConverter) and a much shorter session -
 * 30 minutes, per ADM-01's "session timeout of 30 minutes".
 */
@Service
public class AdminJwtService {

    private static final String ISSUER = "gffh-api";
    private static final long SESSION_TTL_MINUTES = 30;
    private static final long PENDING_2FA_TTL_MINUTES = 5;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public AdminJwtService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    /** Full session token, issued only after the TOTP code is verified. */
    public String issueSessionToken(String adminId, String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(adminId)
                .claim("email", email)
                .claim("role", "PLATFORM_ADMIN")
                .issuedAt(now)
                .expiresAt(now.plus(SESSION_TTL_MINUTES, ChronoUnit.MINUTES))
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

    public long sessionTtlSeconds() {
        return SESSION_TTL_MINUTES * 60;
    }
}
