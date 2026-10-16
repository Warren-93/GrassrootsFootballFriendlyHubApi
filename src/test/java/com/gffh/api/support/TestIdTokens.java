package com.gffh.api.support;

import com.gffh.api.config.OAuthProperties;
import com.gffh.api.config.OAuthTokenConfig;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Google- and Apple-style ID tokens signed with a key generated for the test
 * run, and decoders that trust that key but otherwise run the real checks
 * from {@link OAuthTokenConfig} - so sign-in is tested without reaching
 * Google or Apple.
 */
public final class TestIdTokens {

    public static final String GOOGLE_WEB_CLIENT = "it-web.apps.googleusercontent.com";
    public static final String GOOGLE_IOS_CLIENT = "it-ios.apps.googleusercontent.com";
    public static final String APPLE_SERVICES_ID = "com.kickabout.it.web";
    public static final String APPLE_BUNDLE_ID = "com.gffh.it";

    private static final RSAKey KEY = generateKey("it-provider-key");
    private static final RSAKey OTHER_KEY = generateKey("someone-elses-key");

    private TestIdTokens() {}

    /** Both providers switched on with the IDs above, as the integration tests configure them. */
    public static OAuthProperties configured() {
        OAuthProperties oauth = new OAuthProperties();
        oauth.getGoogle().setClientId(GOOGLE_WEB_CLIENT);
        oauth.getGoogle().setAdditionalClientIds(List.of(GOOGLE_IOS_CLIENT));
        oauth.getApple().setServicesId(APPLE_SERVICES_ID);
        oauth.getApple().setBundleId(APPLE_BUNDLE_ID);
        return oauth;
    }

    /** The production checks, trusting the test key instead of the providers' published ones. */
    public static OAuthTokenConfig.IdTokenDecoders decoders(OAuthProperties oauth) {
        return new OAuthTokenConfig.IdTokenDecoders(
                OAuthTokenConfig.googleDecoder(localDecoder(), oauth),
                OAuthTokenConfig.appleDecoder(localDecoder(), oauth));
    }

    /** A valid Google ID token for the web client, as Google issues them. */
    public static Token google(String email) {
        return new Token().issuer("https://accounts.google.com").audience(GOOGLE_WEB_CLIENT)
                .email(email).claim("email_verified", true);
    }

    /** A valid Apple identity token for the iOS app. Apple sends no name, and email_verified only sometimes. */
    public static Token apple(String email) {
        return new Token().issuer("https://appleid.apple.com").audience(APPLE_BUNDLE_ID).email(email);
    }

    /** A token to sign: valid for an hour unless a setter changes that. */
    public static final class Token {

        private final JWTClaimsSet.Builder claims;
        private RSAKey key = KEY;

        private Token() {
            Instant now = Instant.now();
            claims = new JWTClaimsSet.Builder()
                    .subject(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(Duration.ofHours(1))));
        }

        public Token issuer(String issuer) { claims.issuer(issuer); return this; }

        public Token audience(String audience) { claims.audience(audience); return this; }

        public Token email(String email) { claims.claim("email", email); return this; }

        public Token name(String name) { claims.claim("name", name); return this; }

        /** A null value leaves the claim out. */
        public Token claim(String name, Object value) { claims.claim(name, value); return this; }

        /** An hour-long token ending then. Null leaves the expiry out. */
        public Token expiresAt(Instant expiresAt) {
            if (expiresAt == null) {
                claims.expirationTime(null);
            } else {
                claims.issueTime(Date.from(expiresAt.minus(Duration.ofHours(1)))).expirationTime(Date.from(expiresAt));
            }
            return this;
        }

        public Token signedWithAnotherKey() { key = OTHER_KEY; return this; }

        public String sign() {
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), claims.build());
                jwt.sign(new RSASSASigner(key));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static NimbusJwtDecoder localDecoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
