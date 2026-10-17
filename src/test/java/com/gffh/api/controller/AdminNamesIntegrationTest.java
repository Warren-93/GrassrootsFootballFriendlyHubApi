package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.security.AdminJwtService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ADM-03's verification queue and ADM-04's report queue name the teams involved, not just their IDs. */
class AdminNamesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminJwtService adminJwtService;

    @Test
    @DisplayName("verification queue items carry the team and club name")
    void verificationQueueNamesTheTeamAndClub() throws Exception {
        TestAccount owner = registerAccount("Names Verification Owner");
        String teamId = createTeamInClub(owner.accessToken(), "Names Verify U12s", "Names Verify Club");

        mockMvc.perform(post("/api/v1/teams/" + teamId + "/verification")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "contactDetails": "Sam, 07700900000", "evidenceUrls": ["https://example.com/fa.png"] }
                                """))
                .andExpect(status().isOk());

        JsonNode item = findBy(adminGet("/api/v1/admin/verifications"), "teamId", teamId);
        assertEquals("Names Verify U12s", item.get("teamName").asText());
        assertEquals("Names Verify Club", item.get("clubName").asText());

        mockMvc.perform(get("/api/v1/admin/verifications/" + item.get("id").asText())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").value("Names Verify U12s"))
                .andExpect(jsonPath("$.clubName").value("Names Verify Club"));
    }

    @Test
    @DisplayName("a request rejected once stays in the queue until a second admin confirms")
    void onceRejectedRequestStaysQueuedForSecondAdmin() throws Exception {
        TestAccount owner = registerAccount("Second Review Owner");
        String teamId = createTeamInClub(owner.accessToken(), "Second Review U12s", "Second Review Club");

        mockMvc.perform(post("/api/v1/teams/" + teamId + "/verification")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "contactDetails": "Sam, 07700900000", "evidenceUrls": ["https://example.com/fa.png"] }
                                """))
                .andExpect(status().isOk());
        String id = findBy(adminGet("/api/v1/admin/verifications"), "teamId", teamId).get("id").asText();

        reject(id).andExpect(jsonPath("$.status").value("AWAITING_SECOND_REJECTION"));
        assertEquals("AWAITING_SECOND_REJECTION",
                findBy(adminGet("/api/v1/admin/verifications"), "id", id).get("status").asText());

        // adminToken() mints a different admin each call, so this is the second reviewer.
        reject(id).andExpect(jsonPath("$.status").value("REJECTED"));
        assertFalse(StreamSupport.stream(adminGet("/api/v1/admin/verifications").spliterator(), false)
                .anyMatch(n -> id.equals(n.path("id").asText())));
    }

    @Test
    @DisplayName("report queue items carry the reporter's and the reported team's names")
    void reportQueueNamesBothTeams() throws Exception {
        TestAccount reporter = registerAccount("Names Reporter Owner");
        TestAccount reported = registerAccount("Names Reported Owner");
        String reporterTeam = createTeamInClub(reporter.accessToken(), "Names Reporter FC", "Names Reporter Club");
        String reportedTeam = createTeamInClub(reported.accessToken(), "Names Reported FC", "Names Reported Club");

        String reportId = objectMapper.readTree(mockMvc.perform(post("/api/v1/teams/" + reporterTeam + "/reports")
                        .header("Authorization", "Bearer " + reporter.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reportedTeamId": "%s", "type": "SPAM", "severity": "LOW", "details": "Spam messages" }
                                """.formatted(reportedTeam)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        JsonNode item = findBy(adminGet("/api/v1/admin/reports"), "id", reportId);
        assertEquals("Names Reporter FC", item.get("reporterTeamName").asText());
        assertEquals("Names Reported FC", item.get("reportedTeamName").asText());
    }

    private String adminToken() {
        return adminJwtService.issueSessionToken("it-admin-" + UUID.randomUUID(), "admin@example.com");
    }

    private ResultActions reject(String verificationId) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/verifications/" + verificationId + "/reject")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"reason\": \"Evidence unreadable\" }"))
                .andExpect(status().isOk());
    }

    private JsonNode adminGet(String path) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static JsonNode findBy(JsonNode array, String field, String value) {
        return StreamSupport.stream(array.spliterator(), false)
                .filter(n -> value.equals(n.path(field).asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no item with " + field + "=" + value));
    }

    private String createTeamInClub(String accessToken, String name, String clubName) throws Exception {
        String response = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s", "clubName": "%s",
                                  "ageGroup": "U12", "gender": "MIXED", "abilityLevel": "COMPETITIVE",
                                  "postcode": "FK2 7ZX", "latitude": 56.014582, "longitude": -3.790261,
                                  "travelRadiusMiles": 15, "homeAwayPreference": "EITHER"
                                }
                                """.formatted(name, clubName)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
