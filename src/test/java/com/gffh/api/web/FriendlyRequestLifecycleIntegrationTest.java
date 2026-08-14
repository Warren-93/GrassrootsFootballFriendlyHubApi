package com.gffh.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The core business flow the whole product exists for: propose a friendly,
 * have it accepted, and get a real fixture out of it - then confirm a
 * confirmed fixture can be cancelled again. Nothing on this path had
 * integration coverage before.
 */
class FriendlyRequestLifecycleIntegrationTest extends AbstractIntegrationTest {

    private String publishAndGetSlotId(String accessToken, String teamId, String isoDate) throws Exception {
        String body = """
                { "date": "%s", "startTime": "10:00:00", "endTime": "12:00:00", "homeAwayPreference": "EITHER" }
                """.formatted(isoDate);

        String response = mockMvc.perform(post("/api/v1/teams/" + teamId + "/availability")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    @Test
    @DisplayName("accepting a request creates a confirmed fixture, which can then be cancelled")
    void acceptCreatesAConfirmedFixtureThatCanBeCancelled() throws Exception {
        TestAccount senderOwner = registerAccount("Lifecycle Sender Owner");
        TestAccount recipientOwner = registerAccount("Lifecycle Recipient Owner");
        String senderTeam = createTeam(senderOwner.accessToken(), "Lifecycle FC Sender", 56.014582, -3.790261);
        String recipientTeam = createTeam(recipientOwner.accessToken(), "Lifecycle FC Recipient", 56.014582, -3.790261);

        String senderSlotId = publishAndGetSlotId(senderOwner.accessToken(), senderTeam, "2026-09-12");
        String recipientSlotId = publishAndGetSlotId(recipientOwner.accessToken(), recipientTeam, "2026-09-12");

        String sendBody = """
                {
                  "senderTeamId": "%s",
                  "recipientTeamId": "%s",
                  "senderSlotId": "%s",
                  "recipientSlotId": "%s",
                  "date": "2026-09-12",
                  "startTime": "10:00:00",
                  "endTime": "12:00:00",
                  "homeTeamId": "%s",
                  "costShare": "SPLIT",
                  "refereeArrangement": "NONE",
                  "message": "Fancy a friendly?"
                }
                """.formatted(senderTeam, recipientTeam, senderSlotId, recipientSlotId, senderTeam);

        String sendResponse = mockMvc.perform(post("/api/v1/friendly-requests")
                        .header("Authorization", "Bearer " + senderOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(sendResponse).get("id").asText();

        // Only the recipient may accept - the sender attempting it is rejected.
        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/accept")
                        .header("Authorization", "Bearer " + senderOwner.accessToken()))
                .andExpect(status().is4xxClientError());

        String acceptResponse = mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/accept")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("CONFIRMED", objectMapper.readTree(acceptResponse).get("status").asText(),
                "accepting should resolve all the way to CONFIRMED, not stop at ACCEPTED");

        String fixturesResponse = mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", senderTeam)
                        .header("Authorization", "Bearer " + senderOwner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fixtures = objectMapper.readTree(fixturesResponse);
        assertEquals(1, fixtures.size(), "a confirmed request should produce exactly one fixture");
        JsonNode fixture = fixtures.get(0);
        assertEquals("CONFIRMED", fixture.get("status").asText());
        assertEquals(requestId, fixture.get("friendlyRequestId").asText());
        String fixtureId = fixture.get("id").asText();

        mockMvc.perform(post("/api/v1/fixtures/" + fixtureId + "/cancel")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Pitch unavailable" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("declining a request releases both slots without creating a fixture")
    void decliningReleasesSlotsWithoutCreatingAFixture() throws Exception {
        TestAccount senderOwner = registerAccount("Lifecycle Decline Sender");
        TestAccount recipientOwner = registerAccount("Lifecycle Decline Recipient");
        String senderTeam = createTeam(senderOwner.accessToken(), "Lifecycle FC Decline Sender", 56.014582, -3.790261);
        String recipientTeam = createTeam(recipientOwner.accessToken(), "Lifecycle FC Decline Recipient", 56.014582, -3.790261);

        String senderSlotId = publishAndGetSlotId(senderOwner.accessToken(), senderTeam, "2026-09-19");
        String recipientSlotId = publishAndGetSlotId(recipientOwner.accessToken(), recipientTeam, "2026-09-19");

        String sendBody = """
                {
                  "senderTeamId": "%s",
                  "recipientTeamId": "%s",
                  "senderSlotId": "%s",
                  "recipientSlotId": "%s",
                  "date": "2026-09-19",
                  "startTime": "10:00:00",
                  "endTime": "12:00:00",
                  "homeTeamId": "%s",
                  "costShare": "SPLIT",
                  "refereeArrangement": "NONE"
                }
                """.formatted(senderTeam, recipientTeam, senderSlotId, recipientSlotId, senderTeam);

        String sendResponse = mockMvc.perform(post("/api/v1/friendly-requests")
                        .header("Authorization", "Bearer " + senderOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(sendResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/decline")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Clashes with another fixture" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));

        mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", senderTeam)
                        .header("Authorization", "Bearer " + senderOwner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
