package com.gffh.api.web;

import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SCR-PR-09: the four category toggles plus the email and push switches. */
class NotificationPreferencesIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("email and push default on, and a client that omits them doesn't switch them off")
    void emailAndPushSwitches() throws Exception {
        TestAccount account = registerAccount("Preferences Tester");

        mockMvc.perform(get("/api/v1/me/notification-preferences")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friendlyRequests").value(true))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(true));

        update(account, """
                { "friendlyRequests": true, "fixtures": true, "verification": true, "messages": true,
                  "emailEnabled": false }
                """)
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true));

        // An older client sends only the four category flags.
        update(account, """
                { "friendlyRequests": true, "fixtures": false, "verification": true, "messages": true }
                """)
                .andExpect(jsonPath("$.fixtures").value(false))
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true));

        update(account, """
                { "friendlyRequests": true, "fixtures": true, "verification": true, "messages": true,
                  "emailEnabled": true, "pushEnabled": false }
                """);

        mockMvc.perform(get("/api/v1/me/notification-preferences")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixtures").value(true))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false));
    }

    private ResultActions update(TestAccount account, String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/me/notification-preferences")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
