package com.gffh.api.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Issues and validates the API's own bearer tokens - there is no external
 * identity provider (Technical Specification section 17 defers that
 * decision), so the API is its own authorization server for now.
 *
 * <p>The signing key comes from {@code gffh.jwt.private-key} (env
 * {@code JWT_PRIVATE_KEY}): a PKCS#8 PEM RSA private key, as produced by
 * {@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048}. With a
 * configured key, access tokens survive restarts and redeploys and are valid
 * on every instance. When it is unset (local development, tests) a fresh key
 * pair is generated at startup instead, so a restart invalidates outstanding
 * access tokens and clients fall back to their refresh token, which is stored
 * in Mongo and unaffected.
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    private final RSAKey rsaKey;

    public JwtKeyConfig(@Value("${gffh.jwt.private-key:}") String privateKeyPem) {
        KeyPair keyPair;
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            log.warn("No JWT_PRIVATE_KEY configured - generating an ephemeral signing key; "
                    + "access tokens will not survive a restart");
            keyPair = generateKeyPair();
        } else {
            keyPair = parsePrivateKey(privateKeyPem);
        }
        this.rsaKey = buildKey(keyPair);
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not read the JWT public key", e);
        }
    }

    private static RSAKey buildKey(KeyPair keyPair) {
        try {
            RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .build();
            // A thumbprint key id stays the same for the same key across restarts.
            return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not build the JWT signing key", e);
        }
    }

    static KeyPair parsePrivateKey(String pem) {
        String base64 = pem
                .replace("\\n", "\n")   // tolerate a PEM pasted into a single-line env var
                .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new KeyPair(publicKey, privateKey);
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException(
                    "JWT_PRIVATE_KEY is not a PKCS#8 PEM RSA private key (BEGIN PRIVATE KEY)", e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
    }
}
