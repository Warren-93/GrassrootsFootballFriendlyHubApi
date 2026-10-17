package com.gffh.api.web;

import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The auth HTTP surface is the one thing every other endpoint depends on -
 * if register/login/token validation silently broke, every other test and
 * every client would fail in a way that's hard to attribute. Exercised here
 * against the real filter chain and a real MongoDB, not mocked.
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("register, then use the issued token to call a protected endpoint")
    void registerThenAccessProtectedEndpoint() throws Exception {
        TestAccount account = registerAccount("Auth Flow Tester");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.email()))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    @DisplayName("a protected endpoint rejects requests with no token")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("registering the same email twice is rejected")
    void rejectsDuplicateEmail() throws Exception {
        TestAccount account = registerAccount("First Registration");

        String duplicateBody = """
                {
                  "email": "%s",
                  "password": "AnotherPass123!",
                  "displayName": "Second Registration"
                }
                """.formatted(account.email());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateBody))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("login with the wrong password is rejected without revealing which field was wrong")
    void rejectsLoginWithWrongPassword() throws Exception {
        TestAccount account = registerAccount("Wrong Password Tester");

        String loginBody = """
                {
                  "email": "%s",
                  "password": "TheWrongPassword1!"
                }
                """.formatted(account.email());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login with the correct password issues a fresh usable token")
    void loginIssuesUsableToken() throws Exception {
        TestAccount account = registerAccount("Login Tester");

        String loginBody = """
                {
                  "email": "%s",
                  "password": "TestPass123!"
                }
                """.formatted(account.email());

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String freshToken = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(account.email()));
    }

    @Test
    @DisplayName("Google and Apple sign-in are refused with 400 while they aren't configured")
    void oauthProvidersAreOffByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.google.enabled").value(false))
                .andExpect(jsonPath("$.apple.enabled").value(false));

        for (String provider : new String[] {"google", "apple"}) {
            mockMvc.perform(post("/api/v1/auth/oauth/" + provider)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "idToken": "header.payload.signature" }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("OAUTH_PROVIDER_DISABLED"));
        }
    }

    @Test
    @DisplayName("a malformed bearer token is rejected, not treated as anonymous")
    void rejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("only failed sign-ins count towards the per-email limit")
    void onlyFailedLoginsAreRateLimited() throws Exception {
        TestAccount account = registerAccount("Frequent Signer In");
        for (int i = 0; i < 12; i++) {
            login(account.email(), "TestPass123!").andExpect(status().isOk());
        }
        for (int i = 0; i < 10; i++) {
            login(account.email(), "WrongPass123!").andExpect(status().isUnauthorized());
        }

        login(account.email(), "TestPass123!")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "password": "%s" }
                        """.formatted(email, password)));
    }
}
