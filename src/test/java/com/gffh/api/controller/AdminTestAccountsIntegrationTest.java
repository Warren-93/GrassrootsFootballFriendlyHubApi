package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.security.AdminJwtService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTestAccountsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminJwtService adminJwtService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("a manager test account is saved to test_accounts, signs in, and can be deleted")
    void managerLifecycle() throws Exception {
        JsonNode created = create("""
                { "kind": "MANAGER", "count": 1, "password": "TestPass123!",
                  "team": { "ageGroup": "U10", "gender": "GIRLS" } }
                """).get(0);

        String id = created.get("id").asText();
        String email = created.get("email").asText();
        assertTrue(email.startsWith("kickabout-test+"));
        assertTrue(created.get("emailVerified").asBoolean());
        assertTrue(created.get("teamName").asText().endsWith("U10"));
        assertEquals(1, mongoTemplate.count(new Query(Criteria.where("_id").is(id)), "test_accounts"));

        // A real, usable account: the shared password signs it in.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"%s\", \"password\": \"TestPass123!\" }".formatted(email)))
                .andExpect(status().isOk());

        // The sole club admin is still removable, unlike through the normal user delete.
        mockMvc.perform(delete("/api/v1/admin/test-accounts/" + id).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());

        assertEquals(0, mongoTemplate.count(new Query(Criteria.where("_id").is(id)), "test_accounts"));
        assertEquals(0, mongoTemplate.count(new Query(Criteria.where("email").is(email)), "users"));
        String teamId = created.get("teamId").asText();
        assertTrue(teamRepository.findById(teamId).orElseThrow().isArchived());
    }

    @Test
    @DisplayName("new accounts start unverified and appear in the list")
    void newAccountsListed() throws Exception {
        JsonNode created = create("{ \"kind\": \"NEW\", \"count\": 2, \"password\": \"TestPass123!\" }");
        assertEquals(2, created.size());
        assertFalse(created.get(0).get("emailVerified").asBoolean());

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/test-accounts")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String firstId = created.get(0).get("id").asText();
        assertTrue(list.findValuesAsText("id").contains(firstId));
    }

    @Test
    @DisplayName("an ordinary user cannot create test accounts")
    void ordinaryUserRejected() throws Exception {
        TestAccount user = registerAccount("Not An Admin");
        mockMvc.perform(post("/api/v1/admin/test-accounts")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"kind\": \"NEW\", \"count\": 1, \"password\": \"TestPass123!\" }"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("more than ten at once is refused")
    void countCapped() throws Exception {
        mockMvc.perform(post("/api/v1/admin/test-accounts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"kind\": \"NEW\", \"count\": 11, \"password\": \"TestPass123!\" }"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode create(String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/test-accounts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String adminToken() {
        return adminJwtService.issueSessionToken("it-admin-" + UUID.randomUUID(), "admin@example.com");
    }
}
