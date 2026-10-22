package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Chat screens poll for new messages with {@code ?since=<newest message's
 * time>}, so a poll returns only what's new rather than the whole history,
 * and the database keeps itself tidy with expiry indexes.
 */
class ChatPollingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("since returns only messages from that time on, and no since returns them all")
    void sinceReturnsOnlyNewerMessages() throws Exception {
        Chat chat = startChat("Poll A", "Poll B");
        send(chat.ownerA, chat.conversationId, "First");
        String secondAt = send(chat.ownerB, chat.conversationId, "Second").get("createdAt").asText();
        send(chat.ownerA, chat.conversationId, "Third");

        // Inclusive, so the client's newest message comes back too and is merged by id.
        mockMvc.perform(get("/api/v1/conversations/" + chat.conversationId + "/messages")
                        .param("since", secondAt)
                        .header("Authorization", "Bearer " + chat.ownerA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].body").value("Second"))
                .andExpect(jsonPath("$[1].body").value("Third"));

        mockMvc.perform(get("/api/v1/conversations/" + chat.conversationId + "/messages")
                        .header("Authorization", "Bearer " + chat.ownerA))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("a since in the future returns nothing, and a malformed one is a 400")
    void futureAndMalformedSince() throws Exception {
        Chat chat = startChat("Poll C", "Poll D");
        send(chat.ownerA, chat.conversationId, "Hello");

        mockMvc.perform(get("/api/v1/conversations/" + chat.conversationId + "/messages")
                        .param("since", "2099-01-01T00:00:00Z")
                        .header("Authorization", "Bearer " + chat.ownerB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/conversations/" + chat.conversationId + "/messages")
                        .param("since", "yesterday")
                        .header("Authorization", "Bearer " + chat.ownerB))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("spent tokens and old notifications expire, and messages are indexed by conversation")
    void cleanupIndexesExist() {
        assertThat(ttlSeconds("refreshTokens", "expiresAt")).isEqualTo(7L * 24 * 3600);
        assertThat(ttlSeconds("verificationTokens", "expiresAt")).isEqualTo(7L * 24 * 3600);
        assertThat(ttlSeconds("notifications", "createdAt")).isEqualTo(180L * 24 * 3600);
        assertThat(mongoTemplate.indexOps("messages").getIndexInfo())
                .anySatisfy(i -> assertThat(i.getIndexFields()).extracting(f -> f.getKey())
                        .containsExactly("conversationId", "createdAt"));
    }

    private Long ttlSeconds(String collection, String field) {
        List<IndexInfo> indexes = mongoTemplate.indexOps(collection).getIndexInfo();
        return indexes.stream()
                .filter(i -> i.getIndexFields().size() == 1 && i.getIndexFields().get(0).getKey().equals(field))
                .map(i -> i.getExpireAfter().map(java.time.Duration::getSeconds).orElse(null))
                .findFirst().orElse(null);
    }

    private record Chat(String ownerA, String ownerB, String conversationId) {}

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
        return new Chat(ownerA.accessToken(), ownerB.accessToken(), objectMapper.readTree(response).get("id").asText());
    }

    private JsonNode send(String token, String conversationId, String body) throws Exception {
        Thread.sleep(5); // distinct createdAt milliseconds, so "since" splits the list cleanly
        String response = mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"body\": \"%s\" }".formatted(body)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
