package com.gffh.api.security;

import com.gffh.api.config.OAuthProperties;
import com.gffh.api.config.OAuthTokenConfig;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.enums.OAuthProvider;
import com.gffh.api.support.TestIdTokens;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** What a Google or Apple token must satisfy (OAuthTokenConfig), checked against a local key. */
class OAuthTokenVerifierTest {

    private final OAuthTokenVerifier verifier = verifier(TestIdTokens.configured());

    @Test
    void acceptsGoogleTokensFromEitherIssuer() {
        for (String issuer : OAuthTokenConfig.GOOGLE_ISSUERS) {
            String token = TestIdTokens.google("sam@example.com").issuer(issuer).name("Sam Keeper").sign();

            OAuthTokenVerifier.Identity identity = verifier.verify(OAuthProvider.GOOGLE, token);

            assertEquals("sam@example.com", identity.email());
            assertEquals("Sam Keeper", identity.name());
        }
    }

    @Test
    void acceptsEveryConfiguredGoogleClient() {
        String ios = TestIdTokens.google("sam@example.com").audience(TestIdTokens.GOOGLE_IOS_CLIENT).sign();

        assertEquals("sam@example.com", verifier.verify(OAuthProvider.GOOGLE, ios).email());
    }

    @Test
    void acceptsAppleTokensForTheAppOrTheWebServicesId() {
        String ios = TestIdTokens.apple("relay123@privaterelay.appleid.com").sign();
        String web = TestIdTokens.apple("alex@example.com").audience(TestIdTokens.APPLE_SERVICES_ID)
                .claim("email_verified", "true").sign();

        assertEquals("relay123@privaterelay.appleid.com", verifier.verify(OAuthProvider.APPLE, ios).email());
        assertEquals("alex@example.com", verifier.verify(OAuthProvider.APPLE, web).email());
    }

    @Test
    void normalisesTheEmail() {
        String token = TestIdTokens.google(" Sam.Keeper@Example.COM").sign();

        assertEquals("sam.keeper@example.com", verifier.verify(OAuthProvider.GOOGLE, token).email());
    }

    @Test
    void rejectsATokenIssuedForAnotherApp() {
        assertInvalid(OAuthProvider.GOOGLE, TestIdTokens.google("sam@example.com").audience("someone-else").sign());
        // Our own Apple ID isn't a Google audience, or the other way round.
        assertInvalid(OAuthProvider.GOOGLE,
                TestIdTokens.google("sam@example.com").audience(TestIdTokens.APPLE_BUNDLE_ID).sign());
        assertInvalid(OAuthProvider.APPLE,
                TestIdTokens.apple("sam@example.com").audience(TestIdTokens.GOOGLE_WEB_CLIENT).sign());
    }

    @Test
    void rejectsATokenFromTheWrongIssuer() {
        assertInvalid(OAuthProvider.APPLE, TestIdTokens.google("sam@example.com")
                .audience(TestIdTokens.APPLE_BUNDLE_ID).sign());
        assertInvalid(OAuthProvider.GOOGLE, TestIdTokens.google("sam@example.com")
                .issuer("https://accounts.example.com").sign());
    }

    @Test
    void rejectsExpiredTokensAndTokensWithNoExpiry() {
        assertInvalid(OAuthProvider.GOOGLE, TestIdTokens.google("sam@example.com")
                .expiresAt(Instant.now().minus(Duration.ofMinutes(5))).sign());
        assertInvalid(OAuthProvider.APPLE, TestIdTokens.apple("sam@example.com").expiresAt(null).sign());
    }

    @Test
    void googleMustHaveVerifiedTheEmail() {
        assertInvalid(OAuthProvider.GOOGLE, TestIdTokens.google("sam@example.com")
                .claim("email_verified", false).sign());
        assertInvalid(OAuthProvider.GOOGLE, TestIdTokens.google("sam@example.com")
                .claim("email_verified", null).sign());

        String asString = TestIdTokens.google("sam@example.com").claim("email_verified", "true").sign();
        assertEquals("sam@example.com", verifier.verify(OAuthProvider.GOOGLE, asString).email());
    }

    @Test
    void requiresAnEmailAddress() {
        assertInvalid(OAuthProvider.APPLE, TestIdTokens.apple(null).sign());
        assertInvalid(OAuthProvider.GOOGLE, TestIdTokens.google("not-an-address").sign());
    }

    @Test
    void rejectsForgedAndMalformedTokens() {
        assertInvalid(OAuthProvider.GOOGLE, TestIdTokens.google("sam@example.com").signedWithAnotherKey().sign());
        assertInvalid(OAuthProvider.APPLE, "not-a-jwt");
    }

    @Test
    void aProviderThatIsntConfiguredIsRefusedBeforeAnyCheck() {
        JwtDecoder unused = token -> { throw new AssertionError("should not decode"); };
        OAuthTokenVerifier off = new OAuthTokenVerifier(new OAuthProperties(),
                new OAuthTokenConfig.IdTokenDecoders(unused, unused));

        for (OAuthProvider provider : OAuthProvider.values()) {
            BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> off.verify(provider, "x"));
            assertEquals("OAUTH_PROVIDER_DISABLED", e.code());
            assertEquals(HttpStatus.BAD_REQUEST, e.status());
        }
    }

    @Test
    void unreachableProviderKeysAreNotBlamedOnTheToken() {
        JwtDecoder offline = token -> { throw new JwtException("Couldn't retrieve remote JWK set"); };
        OAuthTokenVerifier verifier = new OAuthTokenVerifier(TestIdTokens.configured(),
                new OAuthTokenConfig.IdTokenDecoders(offline, offline));

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> verifier.verify(OAuthProvider.GOOGLE, TestIdTokens.google("sam@example.com").sign()));
        assertEquals("OAUTH_PROVIDER_UNAVAILABLE", e.code());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.status());
    }

    @Test
    void displayNameIsTheTokensThenTheRequestedThenTheEmailsLocalPart() {
        assertEquals("Sam Keeper",
                new OAuthTokenVerifier.Identity("sam@example.com", " Sam Keeper ").displayName("Ignored"));
        assertEquals("Alex Striker",
                new OAuthTokenVerifier.Identity("alex@example.com", null).displayName(" Alex Striker "));
        assertEquals("relay123",
                new OAuthTokenVerifier.Identity("relay123@privaterelay.appleid.com", " ").displayName(""));
    }

    private void assertInvalid(OAuthProvider provider, String token) {
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> verifier.verify(provider, token));
        assertEquals("INVALID_OAUTH_TOKEN", e.code());
        assertEquals(HttpStatus.UNAUTHORIZED, e.status());
    }

    private static OAuthTokenVerifier verifier(OAuthProperties oauth) {
        return new OAuthTokenVerifier(oauth, TestIdTokens.decoders(oauth));
    }
}
