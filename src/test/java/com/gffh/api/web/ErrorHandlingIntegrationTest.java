package com.gffh.api.web;

import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Requests Spring turns away before a controller runs get the documented
 * error codes - not a 500 - and are never logged as an unhandled exception.
 * Also covers the public client config they share a security rule with.
 */
@ExtendWith(OutputCaptureExtension.class)
class ErrorHandlingIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("routing and input mistakes get 404/400/405/415 with the documented codes")
    void clientMistakesGetClientErrors(CapturedOutput output) throws Exception {
        String token = registerAccount("Error Handling User").accessToken();
        String teamId = createTeam(token, "Error Handling FC", 56.014582, -3.790261);

        mockMvc.perform(get("/api/v1/no-such-thing").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("That address doesn't exist."))
                .andExpect(jsonPath("$.error.requestId").exists());

        mockMvc.perform(get("/api/v1/friendly-requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("teamId is required."));

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/availability").param("from", "not-a-date")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("from is not in the expected format."));

        mockMvc.perform(post("/api/v1/teams").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("The request body isn't valid JSON."));

        mockMvc.perform(post("/api/v1/teams").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Bad Age FC", "ageGroup": "U99" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("ageGroup: unrecognised value."));

        mockMvc.perform(delete("/api/v1/config").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/teams").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=Plain FC"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));

        assertFalse(output.getAll().contains("Unhandled exception"),
                "client mistakes must not be logged as unhandled server errors");
    }

    @Test
    @DisplayName("the client config is public, cacheable, and reports providers off until configured")
    void clientConfigIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/config"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(jsonPath("$.google.enabled").value(false))
                .andExpect(jsonPath("$.google.clientId").doesNotExist())
                .andExpect(jsonPath("$.apple.enabled").value(false))
                .andExpect(jsonPath("$.apple.servicesId").doesNotExist());
    }
}
