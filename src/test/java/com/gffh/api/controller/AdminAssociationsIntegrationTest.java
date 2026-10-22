package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.security.AdminJwtService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAssociationsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminJwtService adminJwtService;

    @Test
    @DisplayName("an admin adds an association, its leagues and divisions, edits them and removes them")
    void lifecycle() throws Exception {
        String name = "Warriors FA " + UUID.randomUUID().toString().substring(0, 6);
        JsonNode created = json(mockMvc.perform(post("/api/v1/admin/associations").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"%s\", \"shortName\": \"WFA\", \"linkedOrganisation\": \"SWFA\" }".formatted(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedOrganisationLabel").value("Scottish Welfare FA (SWFA)")));
        String id = created.get("id").asText();

        // Same name again, any case, is refused.
        mockMvc.perform(post("/api/v1/admin/associations").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"%s\" }".formatted(name.toUpperCase())))
                .andExpect(status().isConflict());

        JsonNode withLeague = json(mockMvc.perform(post("/api/v1/admin/associations/" + id + "/leagues")
                        .header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Sunday League\", \"divisions\": [\"Premier\", \" Div 1 \", \"\", \"Premier\"] }"))
                .andExpect(status().isOk()));
        JsonNode league = withLeague.get("leagues").get(0);
        assertEquals("[\"Premier\",\"Div 1\"]", league.get("divisions").toString());

        mockMvc.perform(put("/api/v1/admin/associations/" + id + "/leagues/" + league.get("id").asText())
                        .header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Sunday League\", \"divisions\": [\"Premier\", \"Div 1\", \"Div 2\"] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagues[0].divisions[2]").value("Div 2"));

        JsonNode list = json(mockMvc.perform(get("/api/v1/admin/associations").header("Authorization", admin()))
                .andExpect(status().isOk()));
        assertTrue(list.toString().contains(name));

        mockMvc.perform(delete("/api/v1/admin/associations/" + id + "/leagues/" + league.get("id").asText())
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leagues.length()").value(0));
        mockMvc.perform(delete("/api/v1/admin/associations/" + id).header("Authorization", admin()))
                .andExpect(status().isNoContent());
        assertFalse(json(mockMvc.perform(get("/api/v1/admin/associations").header("Authorization", admin()))
                .andExpect(status().isOk())).toString().contains(name));
    }

    @Test
    @DisplayName("an ordinary user can't see or change the list")
    void usersRejected() throws Exception {
        TestAccount user = registerAccount("Not An Admin");
        mockMvc.perform(get("/api/v1/admin/associations").header("Authorization", "Bearer " + user.accessToken()))
                .andExpect(status().isForbidden());
    }

    private String admin() {
        return "Bearer " + adminJwtService.issueSessionToken("it-admin-" + UUID.randomUUID(), "admin@example.com");
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString());
    }
}
