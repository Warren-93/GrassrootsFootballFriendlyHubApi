package com.gffh.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Issues access tokens for {@code POST /api/v1/auth/register} and {@code /login}. */
@Service
public class JwtService {

    private static final String ISSUER = "gffh-api";

    private final JwtEncoder jwtEncoder;
    private final long ttlMinutes;

    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${gffh.auth.access-token-ttl-minutes:60}") long ttlMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.ttlMinutes = ttlMinutes;
    }

    public String issueAccessToken(String userId, String email) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(userId)
                .claim("email", email)
                .issuedAt(now)
                .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return ttlMinutes * 60;
    }
}
