package com.gffh.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * API security.
 *
 * <p>Stateless bearer-token authentication, which suits the mobile clients
 * directly. A future browser client should not reuse this flow: tokens in web
 * storage are exposed to any script on the page. The intended approach is a
 * separate cookie-based flow for browsers - httpOnly, Secure, SameSite - or a
 * backend-for-frontend that holds the token server-side. CORS is configured
 * here in anticipation of that, but the browser auth flow itself is still to be
 * designed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(
            @org.springframework.beans.factory.annotation.Value("${gffh.cors.allowed-origins:}")
            List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // Safe only because the API is stateless and token-based. If a
            // cookie flow is added for the web client, CSRF protection must be
            // re-enabled for that flow.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                        "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/confirm",
                        "/api/v1/auth/verify/confirm").permitAll()
                // Resend requires knowing who to resend for, and GET /me is
                // meaningless unauthenticated, so both fall through to
                // anyRequest().authenticated() below rather than being listed here.
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                // Admin sign-in is necessarily unauthenticated (that's the point of
                // it); every other /api/v1/admin/** route requires the role.
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/admin/auth/bootstrap", "/api/v1/admin/auth/login",
                        "/api/v1/admin/auth/login/verify").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("PLATFORM_ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Regular user tokens (from {@code JwtService}) carry no "role" claim and
     * so map to no authorities - fine, since only {@code hasRole(...)} checks
     * on /api/v1/admin/** care. Admin tokens (from {@code AdminJwtService})
     * carry {@code role=PLATFORM_ADMIN}, mapped here to the matching
     * ROLE_PLATFORM_ADMIN authority Spring Security's hasRole(...) expects.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Never a wildcard: the future web client is a known origin, and a
        // wildcard would let any site call the API with a user's token.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
