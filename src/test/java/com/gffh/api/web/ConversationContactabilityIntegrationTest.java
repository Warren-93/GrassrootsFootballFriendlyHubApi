package com.gffh.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The contactability gate is the whole point of the messaging redesign: two
 * teams can chat as soon as one has published availability, without waiting
 * for a friendly request to exist - but a team with no published dates and
 * no history with the other side must not be messageable, or the inbox
 * becomes a spam vector. See ConversationService.
 */
class ConversationContactabilityIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("a team with no published availability and no history cannot be messaged")
    void blocksMessagingATeamWithNoPublishedAvailability() throws Exception {
        TestAccount ownerA = registerAccount("Team A Owner");
        TestAccount ownerB = registerAccount("Team B Owner");
        String teamA = createTeam(ownerA.accessToken(), "Contactability Test A", 56.014582, -3.790261);
        String teamB = createTeam(ownerB.accessToken(), "Contactability Test B", 56.014582, -3.790261);

        String startBody = """
                { "teamId": "%s", "otherTeamId": "%s" }
                """.formatted(teamA, teamB);

        mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + ownerA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_CONTACTABLE"));
    }

    @Test
    @DisplayName("publishing availability unlocks messaging before any request exists, and chat flows both ways")
    void publishingAvailabilityUnlocksMessaging() throws Exception {
        TestAccount ownerA = registerAccount("Team C Owner");
        TestAccount ownerB = registerAccount("Team D Owner");
        String teamA = createTeam(ownerA.accessToken(), "Contactability Test C", 56.014582, -3.790261);
        String teamB = createTeam(ownerB.accessToken(), "Contactability Test D", 56.014582, -3.790261);

        publishAvailability(ownerB.accessToken(), teamB, "2026-09-10");

        String startBody = """
                { "teamId": "%s", "otherTeamId": "%s" }
                """.formatted(teamA, teamB);

        String startResponse = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + ownerA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otherTeam.name").value("Contactability Test D"))
                .andReturn().getResponse().getContentAsString();
        String conversationId = objectMapper.readTree(startResponse).get("id").asText();

        // Starting again returns the same conversation rather than creating a duplicate.
        String secondStart = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + ownerA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(
                conversationId, objectMapper.readTree(secondStart).get("id").asText(),
                "starting a conversation with the same team twice must be idempotent");

        String messageBody = """
                { "body": "Fancy a friendly on the 10th?" }
                """;
        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + ownerA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(messageBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderTeamId").value(teamA));

        String replyBody = """
                { "body": "We're free!" }
                """;
        mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + ownerB.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderTeamId").value(teamB));

        String threadResponse = mockMvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + ownerA.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode messages = objectMapper.readTree(threadResponse);
        org.junit.jupiter.api.Assertions.assertEquals(2, messages.size(), "both messages should be visible to either side");

        // The inbox preview reflects the most recent message.
        mockMvc.perform(get("/api/v1/teams/" + teamB + "/conversations")
                        .header("Authorization", "Bearer " + ownerB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lastMessageBody").value("We're free!"))
                .andExpect(jsonPath("$[0].otherTeam.name").value("Contactability Test C"));
    }

    @Test
    @DisplayName("a team cannot start a conversation on behalf of a team it doesn't manage")
    void blocksStartingOnBehalfOfAnUnmanagedTeam() throws Exception {
        TestAccount ownerA = registerAccount("Team E Owner");
        TestAccount ownerB = registerAccount("Team F Owner");
        TestAccount impostor = registerAccount("Impostor");
        String teamA = createTeam(ownerA.accessToken(), "Contactability Test E", 56.014582, -3.790261);
        String teamB = createTeam(ownerB.accessToken(), "Contactability Test F", 56.014582, -3.790261);
        publishAvailability(ownerB.accessToken(), teamB, "2026-09-10");

        String startBody = """
                { "teamId": "%s", "otherTeamId": "%s" }
                """.formatted(teamA, teamB);

        mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + impostor.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(startBody))
                .andExpect(status().is4xxClientError());
    }
}
