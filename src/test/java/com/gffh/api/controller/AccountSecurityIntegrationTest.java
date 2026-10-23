package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.model.User;
import com.gffh.api.model.enums.VerificationTokenPurpose;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.service.AdminUserService;
import com.gffh.api.service.VerificationTokenService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SCR-PR-08's change-password and change-email actions. */
class AccountSecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private VerificationTokenService verificationTokens;

    @Autowired
    private AdminUserService adminUsers;

    @Autowired
    private UserRepository users;

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

    @Test
    @DisplayName("a link sent to the old address can't verify the new one")
    void oldLinkCannotVerifyNewAddress() throws Exception {
        // Registered with an address they own, so they hold its link...
        TestAccount account = registerAccount("Email Swap Tester");
        // ...then switched to one they don't.
        String newEmail = "someone_else_" + account.email();
        String response = mockMvc.perform(post("/api/v1/me/email")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "newEmail": "%s", "currentPassword": "TestPass123!" }
                                """.formatted(newEmail)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/auth/verify/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "token": "%s" }
                                """.formatted(account.verificationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_VERIFICATION_TOKEN"));
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.emailVerified").value(false));

        // The link sent to the new address still works.
        String token = objectMapper.readTree(response).get("verificationToken").asText();
        mockMvc.perform(post("/api/v1/auth/verify/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "token": "%s" }
                                """.formatted(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    @DisplayName("changing password signs out every other session but keeps the caller's own")
    void changePasswordSignsOutOtherSessions() throws Exception {
        TestAccount account = registerAccount("Password Change Sessions");
        JsonNode thisDevice = login(account.email(), "TestPass123!");
        JsonNode otherDevice = login(account.email(), "TestPass123!");

        mockMvc.perform(post("/api/v1/me/password")
                        .header("Authorization", "Bearer " + thisDevice.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currentPassword": "TestPass123!", "newPassword": "NewPass456!" }
                                """))
                .andExpect(status().isOk());

        refresh(thisDevice.get("refreshToken").asText()).andExpect(status().isOk());
        refresh(otherDevice.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("resetting a password signs the account out everywhere")
    void passwordResetSignsOutEverywhere() throws Exception {
        TestAccount account = registerAccount("Password Reset Sessions");
        JsonNode session = login(account.email(), "TestPass123!");
        String resetToken = verificationTokens.issue(account.userId(), VerificationTokenPurpose.PASSWORD_RESET,
                account.email());

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "token": "%s", "newPassword": "NewPass456!" }
                                """.formatted(resetToken)))
                .andExpect(status().isOk());

        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a suspended account can't refresh its session")
    void suspendedAccountCannotRefresh() throws Exception {
        TestAccount account = registerAccount("Suspended Refresh");
        JsonNode session = login(account.email(), "TestPass123!");
        User user = users.findById(account.userId()).orElseThrow();
        users.save(new User(user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName(),
                user.isEmailVerified(), user.getCreatedAt(), true));

        refresh(session.get("refreshToken").asText())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    @DisplayName("suspending an account ends its sessions, even once it's unsuspended")
    void suspensionEndsSessions() throws Exception {
        TestAccount account = registerAccount("Suspended Sessions");
        JsonNode session = login(account.email(), "TestPass123!");

        adminUsers.suspend("test-admin", account.userId(), "Testing");
        refresh(session.get("refreshToken").asText()).andExpect(status().is4xxClientError());

        adminUsers.unsuspend("test-admin", account.userId(), "Testing");
        refresh(session.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    private JsonNode login(String email, String password) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "%s" }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "refreshToken": "%s" }
                        """.formatted(refreshToken)));
    }
}
