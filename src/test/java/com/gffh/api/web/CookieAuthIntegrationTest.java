package com.gffh.api.web;

import com.gffh.api.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The web app's login flow: the refresh token goes in an httpOnly cookie and
 * never in the response body, and the cookie is only honoured from an exact
 * trusted origin that sent the opt-in header.
 */
class CookieAuthIntegrationTest extends AbstractIntegrationTest {

    private static final String TRUSTED_ORIGIN = "http://localhost:5173";
    private static final Pattern COOKIE_VALUE = Pattern.compile("gffh_refresh=([^;]*)");

    @Test
    @DisplayName("cookie login sets an httpOnly refresh cookie and leaves the token out of the body")
    void loginSetsHttpOnlyCookie() throws Exception {
        TestAccount account = registerAccount("Cookie Login");

        MockHttpServletResponse response = mockMvc.perform(cookieMode(login(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).contains("HttpOnly", "Secure", "SameSite=None", "Path=/api/v1/auth");
        assertThat(cookieValue(response)).isNotBlank();
    }

    @Test
    @DisplayName("refresh reads the cookie, rotates it, and the old cookie value stops working")
    void refreshRotatesCookie() throws Exception {
        TestAccount account = registerAccount("Cookie Refresh");
        String first = cookieValue(mockMvc.perform(cookieMode(login(account))).andReturn().getResponse());

        MockHttpServletResponse refreshed = mockMvc.perform(cookieMode(post("/api/v1/auth/refresh"))
                        .cookie(new Cookie("gffh_refresh", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();
        assertThat(cookieValue(refreshed)).isNotBlank().isNotEqualTo(first);

        mockMvc.perform(cookieMode(post("/api/v1/auth/refresh")).cookie(new Cookie("gffh_refresh", first)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    @DisplayName("the cookie is ignored without the opt-in header or from an untrusted origin")
    void cookieIgnoredOutsideTrustedCookieMode() throws Exception {
        TestAccount account = registerAccount("Cookie Origin");
        String token = cookieValue(mockMvc.perform(cookieMode(login(account))).andReturn().getResponse());

        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", TRUSTED_ORIGIN)
                        .cookie(new Cookie("gffh_refresh", token)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh").header("Origin", "https://attacker.vercel.app")
                        .header("X-Auth-Mode", "cookie")
                        .cookie(new Cookie("gffh_refresh", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an untrusted origin asking for cookie mode gets body tokens and no cookie")
    void untrustedOriginGetsBodyTokens() throws Exception {
        TestAccount account = registerAccount("Preview Login");

        mockMvc.perform(login(account).header("Origin", "https://preview-123.vercel.app")
                        .header("X-Auth-Mode", "cookie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @DisplayName("logout revokes the cookie's token and clears the cookie")
    void logoutRevokesAndClears() throws Exception {
        TestAccount account = registerAccount("Cookie Logout");
        String token = cookieValue(mockMvc.perform(cookieMode(login(account))).andReturn().getResponse());

        mockMvc.perform(cookieMode(post("/api/v1/auth/logout")).cookie(new Cookie("gffh_refresh", token)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(cookieMode(post("/api/v1/auth/refresh")).cookie(new Cookie("gffh_refresh", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("mobile clients still refresh and log out with the token in the body")
    void bodyTokenFlowStillWorks() throws Exception {
        TestAccount account = registerAccount("Body Refresh");
        String body = mockMvc.perform(login(account)).andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(body).get("refreshToken").asText();

        String refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String rotated = objectMapper.readTree(refreshed).get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(rotated)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(rotated)))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder login(TestAccount account) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "password": "TestPass123!" }
                        """.formatted(account.email()));
    }

    private static MockHttpServletRequestBuilder cookieMode(MockHttpServletRequestBuilder request) {
        return request.header("Origin", TRUSTED_ORIGIN).header("X-Auth-Mode", "cookie");
    }

    private static String cookieValue(MockHttpServletResponse response) {
        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        Matcher m = COOKIE_VALUE.matcher(setCookie);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
