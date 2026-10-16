package com.gffh.api.config;

import com.gffh.api.domain.OAuthProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Checks the ID tokens Google and Apple give our clients, for
 * {@code POST /api/v1/auth/oauth/google} and {@code /apple}. A token must be
 * signed with one of the provider's published keys (fetched on first use and
 * cached, so startup needs no network), come from that provider's issuer, be
 * issued for one of our client IDs in {@link OAuthProperties}, be unexpired
 * and carry an email address. Google must also say it has verified that
 * address; Apple only hands out addresses it has verified (private relay ones
 * included).
 *
 * <p>Tests replace the {@link IdTokenDecoders} bean with decoders built on a
 * local key through {@link #googleDecoder} and {@link #appleDecoder}, so the
 * same checks run without reaching Google or Apple.
 */
@Configuration
public class OAuthTokenConfig {

    public static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    public static final List<String> GOOGLE_ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");
    public static final String APPLE_JWK_SET_URI = "https://appleid.apple.com/auth/keys";
    public static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final Duration KEY_FETCH_TIMEOUT = Duration.ofSeconds(5);

    /** One decoder per provider, each with that provider's keys and checks. */
    public record IdTokenDecoders(JwtDecoder google, JwtDecoder apple) {

        public JwtDecoder forProvider(OAuthProvider provider) {
            return switch (provider) {
                case GOOGLE -> google;
                case APPLE -> apple;
            };
        }
    }

    @Bean
    public IdTokenDecoders idTokenDecoders(OAuthProperties oauth) {
        RestOperations keyFetcher = keyFetcher();
        return new IdTokenDecoders(
                googleDecoder(NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI)
                        .restOperations(keyFetcher).build(), oauth),
                appleDecoder(NimbusJwtDecoder.withJwkSetUri(APPLE_JWK_SET_URI)
                        .restOperations(keyFetcher).build(), oauth));
    }

    /** Adds Google's checks to a decoder that already knows which keys to trust. */
    public static NimbusJwtDecoder googleDecoder(NimbusJwtDecoder decoder, OAuthProperties oauth) {
        List<OAuth2TokenValidator<Jwt>> checks = common(GOOGLE_ISSUERS, () -> oauth.getGoogle().audiences());
        checks.add(new JwtClaimValidator<Object>("email_verified", OAuthTokenConfig::isTrue));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(checks));
        return decoder;
    }

    /** Adds Apple's checks to a decoder that already knows which keys to trust. */
    public static NimbusJwtDecoder appleDecoder(NimbusJwtDecoder decoder, OAuthProperties oauth) {
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                common(List.of(APPLE_ISSUER), () -> oauth.getApple().audiences())));
        return decoder;
    }

    // The audiences are read on every check rather than once, so they always
    // match what GET /api/v1/config reports from the same properties.
    private static List<OAuth2TokenValidator<Jwt>> common(List<String> issuers, Supplier<List<String>> audiences) {
        List<OAuth2TokenValidator<Jwt>> checks = new ArrayList<>();
        checks.add(new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                iss -> iss != null && issuers.contains(iss.toString())));
        checks.add(new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.stream().anyMatch(audiences.get()::contains)));
        // JwtTimestampValidator lets a token with no expiry through, so require one.
        checks.add(new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull));
        checks.add(new JwtTimestampValidator());
        checks.add(new JwtClaimValidator<Object>("email", email -> email instanceof String s && s.indexOf('@') > 0));
        return checks;
    }

    /** Google sends a boolean; older tokens (and Apple's) can send the string "true". */
    private static boolean isTrue(Object value) {
        return value != null && "true".equalsIgnoreCase(value.toString());
    }

    private static RestOperations keyFetcher() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(KEY_FETCH_TIMEOUT);
        factory.setReadTimeout(KEY_FETCH_TIMEOUT);
        return new RestTemplate(factory);
    }
}
