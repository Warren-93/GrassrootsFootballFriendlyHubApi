package com.gffh.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for tests that exercise the real HTTP layer end to end against a real
 * MongoDB, per section 22 of the Technical Specification's testing strategy.
 * One container is shared for the whole test JVM (started once, never
 * stopped) rather than per test class, since spinning up Mongo is the
 * expensive part and Spring reuses the same application context across
 * subclasses that share this configuration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    static {
        MONGO.start();
    }

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getConnectionString);
        registry.add("spring.data.mongodb.database", () -> "gffh-test");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** A fresh, uniquely-emailed account so tests never collide on state or the per-email login rate limit. */
    protected record TestAccount(String email, String accessToken, String userId) {}

    protected TestAccount registerAccount(String displayName) throws Exception {
        String email = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
        String body = """
                {
                  "email": "%s",
                  "password": "TestPass123!",
                  "displayName": "%s"
                }
                """.formatted(email, displayName);

        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return new TestAccount(email, json.get("accessToken").asText(), json.get("user").get("id").asText());
    }

    protected String createTeam(String accessToken, String name, double lat, double lon) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "ageGroup": "ADULT",
                  "gender": "MIXED",
                  "abilityLevel": "COMPETITIVE",
                  "format": "ELEVEN_A_SIDE",
                  "postcode": "FK2 7ZX",
                  "latitude": %s,
                  "longitude": %s,
                  "travelRadiusMiles": 15,
                  "homeAwayPreference": "EITHER"
                }
                """.formatted(name, lat, lon);

        String response = mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    protected void publishAvailability(String accessToken, String teamId, String isoDate) throws Exception {
        String body = """
                {
                  "date": "%s",
                  "startTime": "10:00:00",
                  "endTime": "12:00:00",
                  "homeAwayPreference": "EITHER"
                }
                """.formatted(isoDate);

        mockMvc.perform(post("/api/v1/teams/" + teamId + "/availability")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
