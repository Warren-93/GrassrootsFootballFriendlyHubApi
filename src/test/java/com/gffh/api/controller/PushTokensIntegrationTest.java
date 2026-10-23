package com.gffh.api.controller;

import com.gffh.api.model.PushToken;
import com.gffh.api.model.enums.PushPlatform;
import com.gffh.api.repository.PushTokenRepository;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Devices registering for push notifications: POST/DELETE /api/v1/me/push-tokens. */
class PushTokensIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PushTokenRepository pushTokens;

    @Test
    @DisplayName("registering is idempotent, a token moves to whoever registered it last, and only its holder can remove it")
    void registerMoveAndRemove() throws Exception {
        TestAccount first = registerAccount("Push First");
        TestAccount second = registerAccount("Push Second");
        String token = "fcm:" + UUID.randomUUID();

        register(first, "ANDROID", token).andExpect(status().isNoContent());
        register(first, "ANDROID", token).andExpect(status().isNoContent());
        assertEquals(List.of(token), tokensOf(first));

        register(second, "ANDROID", token).andExpect(status().isNoContent());
        assertTrue(tokensOf(first).isEmpty());
        assertEquals(List.of(token), tokensOf(second));

        unregister(first, token).andExpect(status().isNoContent());
        assertEquals(List.of(token), tokensOf(second));

        unregister(second, token).andExpect(status().isNoContent());
        assertTrue(tokensOf(second).isEmpty());
    }

    @Test
    @DisplayName("iOS tokens are stored as lower-case hex; anything else, or an unknown platform, is a 400")
    void validation() throws Exception {
        TestAccount account = registerAccount("Push Validation");
        String hex = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");

        register(account, "IOS", hex.toUpperCase()).andExpect(status().isNoContent());
        List<PushToken> stored = pushTokens.findByUserId(account.userId());
        assertEquals(1, stored.size());
        assertEquals(hex, stored.get(0).getToken());
        assertEquals(PushPlatform.IOS, stored.get(0).getPlatform());

        register(account, "IOS", "not-hex-at-all")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        register(account, "WINDOWS", "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        register(account, "ANDROID", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("signing out isn't required: deleting the account removes its devices")
    void accountDeletionRemovesTokens() throws Exception {
        TestAccount account = registerAccount("Push Deleted");
        register(account, "ANDROID", "fcm:" + UUID.randomUUID()).andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/me")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isNoContent());

        assertTrue(pushTokens.findByUserId(account.userId()).isEmpty());
    }

    @Test
    @DisplayName("push token endpoints need a signed-in user")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/me/push-tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "platform": "ANDROID", "token": "abc" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions register(TestAccount account, String platform, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/me/push-tokens")
                .header("Authorization", "Bearer " + account.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "platform": "%s", "token": "%s" }
                        """.formatted(platform, token)));
    }

    private ResultActions unregister(TestAccount account, String token) throws Exception {
        return mockMvc.perform(delete("/api/v1/me/push-tokens")
                .header("Authorization", "Bearer " + account.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "token": "%s" }
                        """.formatted(token)));
    }

    private List<String> tokensOf(TestAccount account) {
        return pushTokens.findByUserId(account.userId()).stream().map(PushToken::getToken).toList();
    }
}
