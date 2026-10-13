package com.gffh.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;


import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The web client's cookie mode: the refresh token only ever travels in an
 * httpOnly cookie, never in a body script can read, while the mobile body
 * flow keeps working unchanged.
 */
class RefreshCookieIntegrationTest extends AbstractIntegrationTest {

    private static final String MODE = RefreshTokenCookies.MODE_HEADER;

    @Test
    @DisplayName("cookie-mode login sets an httpOnly refresh cookie and leaves it out of the body")
    void loginInCookieModeSetsTheCookie() throws Exception {
        MockHttpServletResponse response = cookieLogin(registerAccount("Cookie Login"));

        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith(RefreshTokenCookies.COOKIE_NAME + "="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Strict"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertTrue(body.hasNonNull("accessToken"));
        assertFalse(body.has("refreshToken"));
    }

    @Test
    @DisplayName("refresh reads the cookie, rotates it, and the old cookie stops working")
    void refreshRotatesTheCookie() throws Exception {
        Cookie first = refreshCookie(cookieLogin(registerAccount("Cookie Refresh")));

        MockHttpServletResponse refreshed = mockMvc.perform(post("/api/v1/auth/refresh").header(MODE, "cookie").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();
        Cookie second = refreshCookie(refreshed);
        assertNotEquals(first.getValue(), second.getValue());

        String accessToken = objectMapper.readTree(refreshed.getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh").header(MODE, "cookie").cookie(first))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the cookie is ignored without the cookie-mode header")
    void cookieAloneIsNotEnough() throws Exception {
        Cookie cookie = refreshCookie(cookieLogin(registerAccount("Cookie No Header")));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout revokes the refresh token and clears the cookie")
    void logoutRevokesAndClears() throws Exception {
        Cookie cookie = refreshCookie(cookieLogin(registerAccount("Cookie Logout")));

        String cleared = mockMvc.perform(post("/api/v1/auth/logout").header(MODE, "cookie").cookie(cookie))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertTrue(cleared.contains("Max-Age=0"));

        mockMvc.perform(post("/api/v1/auth/refresh").header(MODE, "cookie").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the mobile body-token flow is unchanged")
    void bodyFlowStillWorks() throws Exception {
        TestAccount account = registerAccount("Body Refresh");
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(account)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(login).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"refreshToken\": \"%s\" }".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    private MockHttpServletResponse cookieLogin(TestAccount account) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .header(MODE, "cookie")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(account)))
                .andExpect(status().isOk())
                .andReturn().getResponse();
    }

    private static String credentials(TestAccount account) {
        return "{ \"email\": \"%s\", \"password\": \"TestPass123!\" }".formatted(account.email());
    }

    private static Cookie refreshCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(RefreshTokenCookies.COOKIE_NAME);
        assertNotNull(cookie, "expected a refresh cookie");
        return cookie;
    }
}
