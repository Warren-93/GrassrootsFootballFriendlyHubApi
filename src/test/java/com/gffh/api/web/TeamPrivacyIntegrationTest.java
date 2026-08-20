package com.gffh.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SCR-PR-10's "Profile visibility" and "Share contact details" toggles. */
class TeamPrivacyIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("both preferences default to on, round-trip through GET/PATCH, and are manager-only")
    void defaultsAndRoundTrip() throws Exception {
        TestAccount owner = registerAccount("Privacy Test Owner");
        TestAccount impostor = registerAccount("Privacy Test Impostor");
        String teamId = createTeam(owner.accessToken(), "Privacy Test FC", 56.014582, -3.790261);

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/privacy")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchVisible").value(true))
                .andExpect(jsonPath("$.shareContactDetails").value(true));

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/privacy")
                        .header("Authorization", "Bearer " + impostor.accessToken()))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(patch("/api/v1/teams/" + teamId + "/privacy")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "searchVisible": false, "shareContactDetails": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchVisible").value(false))
                .andExpect(jsonPath("$.shareContactDetails").value(false));

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/privacy")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchVisible").value(false))
                .andExpect(jsonPath("$.shareContactDetails").value(false));
    }

    @Test
    @DisplayName("turning off share-contact-details blanks manager name/phone on a confirmed fixture")
    void shareContactDetailsGatesFixtureContact() throws Exception {
        TestAccount senderOwner = registerAccount("Privacy Fixture Sender");
        TestAccount recipientOwner = registerAccount("Privacy Fixture Recipient");
        verifyEmail(senderOwner);
        String senderTeam = createTeam(senderOwner.accessToken(), "Privacy Fixture FC Sender", 56.014582, -3.790261);
        String recipientTeam = createTeam(recipientOwner.accessToken(), "Privacy Fixture FC Recipient", 56.014582, -3.790261);
        completeTeamProfile(senderOwner.accessToken(), senderTeam);
        completeTeamProfile(recipientOwner.accessToken(), recipientTeam);

        mockMvc.perform(patch("/api/v1/teams/" + senderTeam + "/privacy")
                        .header("Authorization", "Bearer " + senderOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "searchVisible": true, "shareContactDetails": false }
                                """))
                .andExpect(status().isOk());

        String senderSlotId = publishSlot(senderOwner.accessToken(), senderTeam, "2026-09-14");
        String recipientSlotId = publishSlot(recipientOwner.accessToken(), recipientTeam, "2026-09-14");

        String sendResponse = mockMvc.perform(post("/api/v1/friendly-requests")
                        .header("Authorization", "Bearer " + senderOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderTeamId": "%s", "recipientTeamId": "%s",
                                  "senderSlotId": "%s", "recipientSlotId": "%s",
                                  "date": "2026-09-14", "startTime": "10:00:00", "endTime": "12:00:00",
                                  "homeTeamId": "%s", "costShare": "SPLIT", "refereeArrangement": "NONE"
                                }
                                """.formatted(senderTeam, recipientTeam, senderSlotId, recipientSlotId, senderTeam)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String requestId = objectMapper.readTree(sendResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/accept")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken()))
                .andExpect(status().isOk());

        String fixturesResponse = mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", senderTeam)
                        .header("Authorization", "Bearer " + senderOwner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fixture = objectMapper.readTree(fixturesResponse).get(0);

        // The sender opted out - blank either way, regardless of who's asking.
        assertNullField(fixture.get("homeTeam"));
        // The recipient never opted out - their contact still comes through.
        org.junit.jupiter.api.Assertions.assertTrue(fixture.get("awayTeam").get("managerName").asText().length() > 0);
    }

    private void assertNullField(JsonNode teamNode) {
        org.junit.jupiter.api.Assertions.assertTrue(teamNode.get("managerName").isNull());
        org.junit.jupiter.api.Assertions.assertTrue(teamNode.get("contactPhone").isNull());
    }

    private String publishSlot(String accessToken, String teamId, String isoDate) throws Exception {
        String response = mockMvc.perform(post("/api/v1/teams/" + teamId + "/availability")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "date": "%s", "startTime": "10:00:00", "endTime": "12:00:00", "homeAwayPreference": "EITHER" }
                                """.formatted(isoDate)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
