package com.gffh.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtKeyConfigTest {

    @Test
    void aConfiguredKeyValidatesTokensAcrossRestarts() throws Exception {
        String pem = newPem();
        String token = encode(new JwtKeyConfig(pem));

        // A second instance built from the same key, as after a redeploy.
        assertEquals("user-1", new JwtKeyConfig(pem).jwtDecoder().decode(token).getSubject());
    }

    @Test
    void acceptsAPemPastedAsASingleLineWithEscapedNewlines() throws Exception {
        String pem = newPem();
        String token = encode(new JwtKeyConfig(pem));

        assertEquals("user-1", new JwtKeyConfig(pem.replace("\n", "\\n")).jwtDecoder().decode(token).getSubject());
    }

    @Test
    void withoutAConfiguredKeyEachStartupUsesItsOwnKey() {
        String token = encode(new JwtKeyConfig(""));

        assertThrows(JwtException.class, () -> new JwtKeyConfig("").jwtDecoder().decode(token));
    }

    @Test
    void rejectsSomethingThatIsNotAKey() {
        assertThrows(IllegalStateException.class, () -> new JwtKeyConfig("not a key"));
    }

    private static String encode(JwtKeyConfig config) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gffh-api").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();
        return config.jwtEncoder().encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private static String newPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String body = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }
}
