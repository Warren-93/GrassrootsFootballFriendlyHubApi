package com.gffh.api.controller;

import com.gffh.api.security.AdminJwtService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The chat language filter is enforced by the API, so it holds for every
 * client: blocked words are masked before a message is stored, the admin
 * list changes what's masked (including in older messages), and GIFs are
 * refused while no GIPHY key is configured.
 */
class ChatFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminJwtService adminJwtService;

    @AfterEach
    void restoreDefaultList() throws Exception {
        mockMvc.perform(post("/api/v1/admin/chat-filter/reset").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("blocked words are masked for both sides and in the inbox preview")
    void masksBlockedWords() throws Exception {
        Chat chat = startChat("Filter A", "Filter B");

        send(chat.ownerA, chat.conversationId, "{ \"body\": \"That pitch was sh1t, sorry!\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("That pitch was ****, sorry!"));

        mockMvc.perform(get("/api/v1/conversations/" + chat.conversationId + "/messages")
                        .header("Authorization", "Bearer " + chat.ownerB))
                .andExpect(jsonPath("$[0].body").value("That pitch was ****, sorry!"));
        mockMvc.perform(get("/api/v1/teams/" + chat.teamB + "/conversations")
                        .header("Authorization", "Bearer " + chat.ownerB))
                .andExpect(jsonPath("$[0].lastMessageBody").value("That pitch was ****, sorry!"));
    }

    @Test
    @DisplayName("an admin's word list replaces the starter list and also masks older messages")
    void adminListAppliesToNewAndOlderMessages() throws Exception {
        Chat chat = startChat("Filter C", "Filter D");
        send(chat.ownerA, chat.conversationId, "{ \"body\": \"Bring the bibs\" }").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/chat-filter").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usingDefaults").value(true));

        mockMvc.perform(put("/api/v1/admin/chat-filter")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"words\": [\"Bibs\", \"bibs\", \"\"] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usingDefaults").value(false))
                .andExpect(jsonPath("$.words.length()").value(1))
                .andExpect(jsonPath("$.words[0]").value("bibs"));

        mockMvc.perform(get("/api/v1/conversations/" + chat.conversationId + "/messages")
                        .header("Authorization", "Bearer " + chat.ownerB))
                .andExpect(jsonPath("$[0].body").value("Bring the ****"));
        // The starter list no longer applies.
        send(chat.ownerA, chat.conversationId, "{ \"body\": \"shit\" }")
                .andExpect(jsonPath("$.body").value("shit"));

        mockMvc.perform(post("/api/v1/admin/chat-filter/preview")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"text\": \"Wear BIBS\" }"))
                .andExpect(jsonPath("$.text").value("Wear ****"))
                .andExpect(jsonPath("$.masked").value(true));
    }

    @Test
    @DisplayName("team managers can't edit the word list")
    void wordListIsAdminOnly() throws Exception {
        TestAccount user = registerAccount("Filter Nosy");
        mockMvc.perform(put("/api/v1/admin/chat-filter")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"words\": [] }"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an empty message is refused, and so is a GIF while GIFs are off")
    void emptyAndGifMessages() throws Exception {
        Chat chat = startChat("Filter E", "Filter F");
        send(chat.ownerA, chat.conversationId, "{ \"body\": \"   \" }")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EMPTY_MESSAGE"));
        send(chat.ownerA, chat.conversationId, "{ \"gifId\": \"abc123\" }")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("GIFS_DISABLED"));
        mockMvc.perform(get("/api/v1/config"))
                .andExpect(jsonPath("$.gifsEnabled").value(false));
    }

    private record Chat(String ownerA, String ownerB, String teamB, String conversationId) {}

    private Chat startChat(String nameA, String nameB) throws Exception {
        TestAccount ownerA = registerAccount(nameA + " Owner");
        TestAccount ownerB = registerAccount(nameB + " Owner");
        String teamA = createTeam(ownerA.accessToken(), nameA, 56.014582, -3.790261);
        String teamB = createTeam(ownerB.accessToken(), nameB, 56.014582, -3.790261);
        publishAvailability(ownerB.accessToken(), teamB, daysFromNow(7));
        String response = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", "Bearer " + ownerA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"teamId\": \"%s\", \"otherTeamId\": \"%s\" }".formatted(teamA, teamB)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Chat(ownerA.accessToken(), ownerB.accessToken(), teamB,
                objectMapper.readTree(response).get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions send(String token, String conversationId, String json)
            throws Exception {
        return mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String adminToken() {
        return adminJwtService.issueSessionToken("it-admin-" + UUID.randomUUID(), "admin@example.com");
    }
}
