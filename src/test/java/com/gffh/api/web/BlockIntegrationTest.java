package com.gffh.api.web;

import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SCR-PR-11's list/unblock side - the block action itself already had
 * coverage elsewhere; this covers the part that was missing an endpoint
 * entirely until now (see BlockController).
 */
class BlockIntegrationTest extends AbstractIntegrationTest {

    private String blockBody(String blockedTeamId) {
        return """
                { "blockedTeamId": "%s", "reason": "Testing" }
                """.formatted(blockedTeamId);
    }

    @Test
    @DisplayName("a team can list and unblock the teams it has blocked, and only those")
    void listAndUnblock() throws Exception {
        TestAccount ownerA = registerAccount("Block Test Owner A");
        String teamA = createTeam(ownerA.accessToken(), "Block Test FC A", 56.014582, -3.790261);

        TestAccount ownerB = registerAccount("Block Test Owner B");
        String teamB = createTeam(ownerB.accessToken(), "Block Test FC B", 56.014582, -3.790261);

        TestAccount ownerC = registerAccount("Block Test Owner C");
        String teamC = createTeam(ownerC.accessToken(), "Block Test FC C", 56.014582, -3.790261);

        mockMvc.perform(post("/api/v1/teams/" + teamA + "/blocks")
                        .header("Authorization", "Bearer " + ownerA.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockBody(teamB)))
                .andExpect(status().isOk());

        // Empty until A blocks someone from A's own side - B blocking nobody sees nothing.
        mockMvc.perform(get("/api/v1/teams/" + teamB + "/blocks")
                        .header("Authorization", "Bearer " + ownerB.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String listResponse = mockMvc.perform(get("/api/v1/teams/" + teamA + "/blocks")
                        .header("Authorization", "Bearer " + ownerA.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].blockedTeamId").value(teamB))
                .andExpect(jsonPath("$[0].blockedTeamName").value("Block Test FC B"))
                .andExpect(jsonPath("$[0].reason").value("Testing"))
                .andReturn().getResponse().getContentAsString();
        String blockId = objectMapper.readTree(listResponse).get(0).get("id").asText();

        // Another team's manager can't unblock a block they don't own.
        mockMvc.perform(delete("/api/v1/teams/" + teamC + "/blocks/" + blockId)
                        .header("Authorization", "Bearer " + ownerC.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/teams/" + teamA + "/blocks/" + blockId)
                        .header("Authorization", "Bearer " + ownerA.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/teams/" + teamA + "/blocks")
                        .header("Authorization", "Bearer " + ownerA.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("a manager can't list or unblock on behalf of a team they don't manage")
    void requiresManagingTheTeam() throws Exception {
        TestAccount ownerA = registerAccount("Block Test Owner D");
        String teamA = createTeam(ownerA.accessToken(), "Block Test FC D", 56.014582, -3.790261);
        TestAccount impostor = registerAccount("Block Test Impostor");

        mockMvc.perform(get("/api/v1/teams/" + teamA + "/blocks")
                        .header("Authorization", "Bearer " + impostor.accessToken()))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(delete("/api/v1/teams/" + teamA + "/blocks/nonexistent-id")
                        .header("Authorization", "Bearer " + impostor.accessToken()))
                .andExpect(status().is4xxClientError());
    }
}
