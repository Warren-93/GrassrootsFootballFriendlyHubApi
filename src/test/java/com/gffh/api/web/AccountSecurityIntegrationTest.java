package com.gffh.api.web;

import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SCR-PR-08's change-password and change-email actions. */
class AccountSecurityIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("changing password requires the current one, and the new one then works to sign in")
    void changePassword() throws Exception {
        TestAccount account = registerAccount("Password Change Tester");

        mockMvc.perform(post("/api/v1/me/password")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currentPassword": "WrongPass123!", "newPassword": "NewPass456!" }
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/me/password")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currentPassword": "TestPass123!", "newPassword": "NewPass456!" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "NewPass456!" }
                                """.formatted(account.email())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("changing email requires the current password, rejects a duplicate, and un-verifies the account")
    void changeEmail() throws Exception {
        TestAccount account = registerAccount("Email Change Tester");
        verifyEmail(account);
        TestAccount other = registerAccount("Email Change Other");

        mockMvc.perform(post("/api/v1/me/email")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "newEmail": "%s", "currentPassword": "TestPass123!" }
                                """.formatted(other.email())))
                .andExpect(status().isConflict());

        String newEmail = "changed_" + account.email();
        String response = mockMvc.perform(post("/api/v1/me/email")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "newEmail": "%s", "currentPassword": "TestPass123!" }
                                """.formatted(newEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(newEmail))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.verificationToken").exists())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("verificationToken").asText();
        mockMvc.perform(post("/api/v1/auth/verify/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "token": "%s" }
                                """.formatted(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }
}
