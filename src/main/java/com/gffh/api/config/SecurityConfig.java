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
 * <p>Stateless bearer-token authentication. The mobile clients hold both
 * tokens themselves; the web client holds only the access token, in memory,
 * and its refresh token lives in an httpOnly cookie that is only used by the
 * /api/v1/auth endpoints (see {@link com.gffh.api.security.RefreshCookies}).
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
            // Every endpoint except the refresh-cookie ones authenticates with
            // a bearer header, which a cross-site request can't forge. The
            // cookie endpoints (refresh, logout) are protected instead by
            // RefreshCookies: they need a non-safelisted header, so a preflight
            // from an exact trusted origin, before the cookie is read.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                        "/api/v1/auth/logout", "/api/v1/auth/oauth/google", "/api/v1/auth/oauth/apple",
                        "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/confirm",
                        "/api/v1/auth/verify/confirm").permitAll()
                // Resend requires knowing who to resend for, and GET /me is
                // meaningless unauthenticated, so both fall through to
                // anyRequest().authenticated() below rather than being listed here.
                // The client config (support email, which sign-in buttons to
                // show) is needed before anyone has signed in.
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/config").permitAll()
                // Metrics and the rest describe the server, not the user: any
                // signed-in user (anyone, since sign-up is open) mustn't read them.
                .requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN")
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
        // Exact origins from gffh.cors.allowed-origins, plus a scoped pattern
        // for Vercel preview deployments - each branch push gets its own
        // random *.vercel.app subdomain, so an exact-match list can't cover
        // them. Never a bare "*": the pattern is still scoped to the vercel.app
        // domain rather than any origin.
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedOriginPatterns(List.of("https://*.vercel.app"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key",
                com.gffh.api.security.RefreshCookies.MODE_HEADER));
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
