package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.config.OAuthProperties;
import com.gffh.api.config.OAuthTokenConfig;
import com.gffh.api.model.User;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.support.AbstractIntegrationTest;
import com.gffh.api.support.TestIdTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Google and Apple sign-in end to end: the real filter chain, token checks
 * and MongoDB, with only the providers' published keys swapped for a local
 * one (see TestIdTokens). Both providers are switched on here; the
 * "not configured" answer is covered by AuthFlowIntegrationTest, which runs
 * with the defaults.
 */
@TestPropertySource(properties = {
        "gffh.oauth.google.client-id=" + TestIdTokens.GOOGLE_WEB_CLIENT,
        "gffh.oauth.google.additional-client-ids=" + TestIdTokens.GOOGLE_IOS_CLIENT,
        "gffh.oauth.apple.services-id=" + TestIdTokens.APPLE_SERVICES_ID,
        "gffh.oauth.apple.bundle-id=" + TestIdTokens.APPLE_BUNDLE_ID})
class OAuthSignInIntegrationTest extends AbstractIntegrationTest {

    private static final String TRUSTED_ORIGIN = "http://localhost:5173";

    /** Trusts the test key instead of Google's and Apple's, keeping every other check. */
    @TestConfiguration
    static class LocalProviderKeys {

        @Bean
        @Primary
        OAuthTokenConfig.IdTokenDecoders localIdTokenDecoders(OAuthProperties oauth) {
            return TestIdTokens.decoders(oauth);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("the client config reports both providers on, from the same settings sign-in uses")
    void configReportsTheProviders() throws Exception {
        mockMvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google.enabled").value(true))
                .andExpect(jsonPath("$.google.clientId").value(TestIdTokens.GOOGLE_WEB_CLIENT))
                .andExpect(jsonPath("$.apple.enabled").value(true))
                .andExpect(jsonPath("$.apple.servicesId").value(TestIdTokens.APPLE_SERVICES_ID));
    }

    @Test
    @DisplayName("a first Google sign-in creates a verified account with no password, and the next one finds it")
    void googleCreatesANewUser() throws Exception {
        String email = newEmail();

        String body = signIn("google", TestIdTokens.google(email).name("Sam Keeper").sign(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.verificationToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.displayName").value("Sam Keeper"))
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                .andReturn().getResponse().getContentAsString();
        JsonNode tokens = objectMapper.readTree(body);
        String userId = tokens.get("user").get("id").asText();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.emailVerified").value(true));
        // No password was set, so password sign-in fails until they use "Forgot password".
        login(email, "AnyPassword123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        signIn("google", TestIdTokens.google(email).name("Sam Keeper").sign(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.user.id").value(userId));
    }

    @Test
    @DisplayName("Google sign-in links to an existing verified account by email, which keeps its password")
    void googleLinksAnExistingUser() throws Exception {
        TestAccount account = registerAccount("Linked Player");
        verifyEmail(account);

        signIn("google", TestIdTokens.google(account.email().toUpperCase())
                        .audience(TestIdTokens.GOOGLE_IOS_CLIENT).name("Someone Else").sign(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.user.id").value(account.userId()))
                .andExpect(jsonPath("$.user.displayName").value("Linked Player"))
                .andExpect(jsonPath("$.user.emailVerified").value(true));

        // Password login still works, and never mentions newUser.
        login(account.email(), "TestPass123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").doesNotExist());
    }

    @Test
    @DisplayName("Google sign-in verifies an unverified account, dropping the password and sessions nobody had proved")
    void googleVerifiesAnUnverifiedUser() throws Exception {
        TestAccount account = registerAccount("Unverified Player");
        String earlierRefreshToken = objectMapper.readTree(login(account.email(), "TestPass123!")
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("refreshToken").asText();

        signIn("google", TestIdTokens.google(account.email()).sign(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(false))
                .andExpect(jsonPath("$.user.id").value(account.userId()))
                .andExpect(jsonPath("$.user.emailVerified").value(true));

        login(account.email(), "TestPass123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(earlierRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Apple sign-in names a new account from the request, else from the email")
    void appleCreatesNewUsers() throws Exception {
        String named = newEmail();
        signIn("apple", TestIdTokens.apple(named).sign(), "Alex Striker")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.user.displayName").value("Alex Striker"))
                .andExpect(jsonPath("$.user.emailVerified").value(true));

        String relay = "relay" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)
                + "@privaterelay.appleid.com";
        signIn("apple", TestIdTokens.apple(relay).audience(TestIdTokens.APPLE_SERVICES_ID).sign(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.user.email").value(relay))
                .andExpect(jsonPath("$.user.displayName").value(relay.substring(0, relay.indexOf('@'))));
    }

    @Test
    @DisplayName("a team invite waiting for the address becomes a membership on Google sign-in")
    void googleAcceptsPendingInvites() throws Exception {
        TestAccount owner = registerAccount("OAuth Invite Owner");
        verifyEmail(owner);
        String teamId = createTeam(owner.accessToken(), "OAuth Invite FC", 56.014582, -3.790261);
        String email = newEmail();
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "role": "TEAM_MANAGER" }
                                """.formatted(email)))
                .andExpect(status().isAccepted());

        signIn("google", TestIdTokens.google(email).sign(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newUser").value(true));

        JsonNode members = objectMapper.readTree(mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode member = StreamSupport.stream(members.spliterator(), false)
                .filter(m -> email.equals(m.path("email").asText()))
                .findFirst().orElseThrow(() -> new AssertionError("the invite wasn't accepted"));
        assertThat(member.get("role").asText()).isEqualTo("TEAM_MANAGER");
    }

    @Test
    @DisplayName("a token issued for another app is refused with 401 and creates nothing")
    void wrongAudienceIsRejected() throws Exception {
        String email = newEmail();

        signIn("google", TestIdTokens.google(email).audience("another-app.apps.googleusercontent.com").sign(), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_OAUTH_TOKEN"));
        signIn("apple", TestIdTokens.apple(email).audience("com.someone.else").sign(), "Nobody")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_OAUTH_TOKEN"));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("expired, unverified, forged and missing tokens are refused")
    void otherBadTokensAreRejected() throws Exception {
        String email = newEmail();

        signIn("google", TestIdTokens.google(email).expiresAt(Instant.now().minus(Duration.ofMinutes(5))).sign(), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_OAUTH_TOKEN"));
        signIn("google", TestIdTokens.google(email).claim("email_verified", false).sign(), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_OAUTH_TOKEN"));
        signIn("apple", TestIdTokens.apple(email).signedWithAnotherKey().sign(), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_OAUTH_TOKEN"));
        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    @DisplayName("cookie mode sets the httpOnly refresh cookie and leaves the token out of the body, like login")
    void cookieModeSetsTheRefreshCookie() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .header("Origin", TRUSTED_ORIGIN)
                        .header("X-Auth-Mode", "cookie")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TestIdTokens.google(newEmail()).sign(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.newUser").value(true))
                .andReturn().getResponse();

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("gffh_refresh=")
                .contains("HttpOnly", "Secure", "SameSite=None", "Path=/api/v1/auth")
                .doesNotStartWith("gffh_refresh=;");

        // The cookie is a working refresh token.
        String token = setCookie.substring("gffh_refresh=".length(), setCookie.indexOf(';'));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", TRUSTED_ORIGIN)
                        .header("X-Auth-Mode", "cookie")
                        .cookie(new Cookie("gffh_refresh", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("a suspended account can't sign in with Google either")
    void suspendedAccountsAreRefused() throws Exception {
        TestAccount account = registerAccount("Suspended Player");
        User user = userRepository.findById(account.userId()).orElseThrow();
        userRepository.save(new User(user.id(), user.email(), user.passwordHash(), user.displayName(),
                user.emailVerified(), user.createdAt(), true));

        signIn("google", TestIdTokens.google(account.email()).sign(), null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("Google sign-in shares the per-email limit with password login")
    void sharesTheLoginRateLimit() throws Exception {
        TestAccount account = registerAccount("Rate Limited Player");
        for (int i = 0; i < 10; i++) {
            login(account.email(), "WrongPass123!").andExpect(status().isUnauthorized());
        }

        signIn("google", TestIdTokens.google(account.email()).sign(), null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    private ResultActions signIn(String provider, String idToken, String displayName) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/oauth/" + provider)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(idToken, displayName)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(email, password)));
    }

    private String body(String idToken, String displayName) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("idToken", idToken);
        if (displayName != null) body.put("displayName", displayName);
        return objectMapper.writeValueAsString(body);
    }

    private static String newEmail() {
        return "oauth_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
    }
}
