package com.gffh.api.security;

import com.gffh.api.config.OAuthProperties;
import com.gffh.api.config.OAuthTokenConfig;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.enums.OAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Turns a Google or Apple ID token into the email address it proves the
 * caller owns. What a token must satisfy is set out in {@link OAuthTokenConfig};
 * whether each provider is on comes from {@link OAuthProperties}, the same
 * settings {@code GET /api/v1/config} reports to the clients.
 */
@Component
public class OAuthTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenVerifier.class);

    /** Who the provider vouched for: a verified email address, and their name when the token has one. */
    public record Identity(String email, String name) {

        /**
         * The token's name, else the one the app sent (Apple only ever gives
         * the name to the app, and only on the first sign-in), else the
         * email's local part.
         */
        public String displayName(String requested) {
            if (name != null && !name.isBlank()) return name.trim();
            if (requested != null && !requested.isBlank()) return requested.trim();
            int at = email.indexOf('@');
            return at > 0 ? email.substring(0, at) : email;
        }
    }

    private final OAuthProperties oauth;
    private final OAuthTokenConfig.IdTokenDecoders decoders;

    public OAuthTokenVerifier(OAuthProperties oauth, OAuthTokenConfig.IdTokenDecoders decoders) {
        this.oauth = oauth;
        this.decoders = decoders;
    }

    public Identity verify(OAuthProvider provider, String idToken) {
        if (!enabled(provider)) {
            throw new BusinessRuleException("OAUTH_PROVIDER_DISABLED", HttpStatus.BAD_REQUEST,
                    "Sign in with " + provider.label() + " isn't set up on this server.");
        }
        Jwt jwt;
        try {
            jwt = decoders.forProvider(provider).decode(idToken);
        } catch (BadJwtException e) {
            // The reason ("The aud claim is not valid") says which setting to
            // check when a client is misconfigured; it never includes the token.
            log.info("{} sign-in token rejected: {}", provider.label(), e.getMessage());
            throw new BusinessRuleException("INVALID_OAUTH_TOKEN", HttpStatus.UNAUTHORIZED,
                    "That " + provider.label() + " sign-in couldn't be verified. Please try again.");
        } catch (JwtException e) {
            // Not the token's fault: the provider's signing keys couldn't be fetched.
            log.warn("Could not check a {} sign-in token: {}", provider.label(), e.getMessage());
            throw new BusinessRuleException("OAUTH_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "We couldn't reach " + provider.label() + " to check your sign-in. Please try again.");
        }
        return new Identity(jwt.getClaimAsString("email").trim().toLowerCase(Locale.ROOT),
                jwt.getClaimAsString("name"));
    }

    private boolean enabled(OAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> oauth.getGoogle().isEnabled();
            case APPLE -> oauth.getApple().isEnabled();
        };
    }
}
