package com.gffh.api.controller;

import com.gffh.api.config.LegacyDivisionMigration;
import com.gffh.api.support.AbstractIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Teams carry an SFA organisation and a division; old level names keep working. */
class TeamDivisionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private LegacyDivisionMigration migration;

    @Test
    @DisplayName("an old level name is accepted, and organisation and division round-trip through PATCH")
    void organisationAndDivisionRoundTrip() throws Exception {
        TestAccount owner = registerAccount("Division Test Owner");
        // createTeam still sends the old "COMPETITIVE" level, as app builds before divisions do.
        String teamId = createTeam(owner.accessToken(), "Division Test FC", 56.014582, -3.790261);

        mockMvc.perform(get("/api/v1/teams/" + teamId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abilityLevel").value("DIVISION_1"))
                .andExpect(jsonPath("$.organisation").doesNotExist());

        mockMvc.perform(patch("/api/v1/teams/" + teamId)
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "organisation": "SWFA", "abilityLevel": "DIVISION_3_PLUS", "league": "Warriors FA" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisation").value("SWFA"))
                .andExpect(jsonPath("$.abilityLevel").value("DIVISION_3_PLUS"))
                .andExpect(jsonPath("$.league").value("Warriors FA"));
    }

    @Test
    @DisplayName("the startup migration rewrites stored legacy levels to divisions")
    void migrationRewritesLegacyValues() throws Exception {
        TestAccount owner = registerAccount("Division Migration Owner");
        String teamId = createTeam(owner.accessToken(), "Division Migration FC", 56.014582, -3.790261);
        Query byId = new Query(Criteria.where("_id").is(teamId));
        mongoTemplate.updateFirst(byId, new Update().set("abilityLevel", "INTERMEDIATE"), "teams");

        migration.migrate();

        Document stored = mongoTemplate.findOne(byId, Document.class, "teams");
        assertThat(stored.getString("abilityLevel")).isEqualTo("DIVISION_2");
    }
}
