package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.model.enums.AdminRole;
import com.gffh.api.repository.PlatformAdminRepository;
import com.gffh.api.security.AdminJwtService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSessionRefreshIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminJwtService adminJwtService;

    @Autowired
    private PlatformAdminRepository admins;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("a signed-in admin swaps their token for a fresh one that keeps the session's start time")
    void refreshIssuesNewToken() throws Exception {
        PlatformAdmin admin = newAdmin();
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String old = adminJwtService.issueSessionToken(admin.id(), admin.email(), start);

        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/auth/refresh")
                        .header("Authorization", "Bearer " + old))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String fresh = body.get("accessToken").asText();
        assertEquals(admin.email(), body.get("email").asText());
        assertEquals(1800, body.get("expiresIn").asLong());
        Jwt decoded = jwtDecoder.decode(fresh);
        assertEquals(start, adminJwtService.sessionStart(decoded));
        mockMvc.perform(get("/api/v1/admin/admins/me").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a refresh near the session's maximum length never runs past it")
    void refreshCappedAtMaxLength() throws Exception {
        PlatformAdmin admin = newAdmin();
        // 12-hour cap, signed in 11h50m ago: only ten minutes are left.
        Instant start = Instant.now().minus(11 * 60 + 50, ChronoUnit.MINUTES);
        String old = adminJwtService.issueSessionToken(admin.id(), admin.email(), start);

        JsonNode body = objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/auth/refresh")
                        .header("Authorization", "Bearer " + old))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertTrue(body.get("expiresIn").asLong() <= 600);
        Jwt fresh = jwtDecoder.decode(body.get("accessToken").asText());
        assertFalse(fresh.getExpiresAt().isAfter(adminJwtService.sessionEnd(start.truncatedTo(ChronoUnit.SECONDS))));
    }

    @Test
    @DisplayName("a removed admin can't keep extending their session")
    void removedAdminRefused() throws Exception {
        String token = adminJwtService.issueSessionToken("it-admin-" + UUID.randomUUID(), "gone@example.com");
        mockMvc.perform(post("/api/v1/admin/auth/refresh").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh needs an admin token")
    void needsAdminToken() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/refresh")).andExpect(status().isUnauthorized());
        TestAccount user = registerAccount("Not An Admin");
        mockMvc.perform(post("/api/v1/admin/auth/refresh").header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isForbidden());
    }

    private PlatformAdmin newAdmin() {
        return admins.save(new PlatformAdmin(null, "it-refresh-" + UUID.randomUUID() + "@example.com",
                "hash", "SECRET", Instant.now(), null, AdminRole.ADMIN));
    }
}
