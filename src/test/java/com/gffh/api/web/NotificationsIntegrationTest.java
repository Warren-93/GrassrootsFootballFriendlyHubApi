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
 * SCR-HM-02's notification centre - mark-all-read and clear-all are the two
 * bulk actions the web client's notification modal offers, on top of the
 * per-notification read already covered by FriendlyRequestLifecycleIntegrationTest's
 * flow. Notifications are only ever created as a side effect of another
 * action (see NotificationService.notifyTeam), so a real friendly request is
 * sent here to produce a genuine one rather than seeding one directly.
 */
class NotificationsIntegrationTest extends AbstractIntegrationTest {

    private String publishAndGetSlotId(String accessToken, String teamId, String isoDate) throws Exception {
        String body = """
                { "date": "%s", "startTime": "10:00:00", "endTime": "12:00:00", "homeAwayPreference": "EITHER" }
                """.formatted(isoDate);

        String response = mockMvc.perform(post("/api/v1/teams/" + teamId + "/availability")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asText();
    }

    private TestAccount sendAFriendlyRequestTo(TestAccount recipientOwner, String recipientTeam) throws Exception {
        TestAccount senderOwner = registerAccount("Notif Sender Owner");
        String senderTeam = createTeam(senderOwner.accessToken(), "Notif FC Sender", 56.014582, -3.790261);
        completeTeamProfile(senderOwner.accessToken(), senderTeam);

        String senderSlotId = publishAndGetSlotId(senderOwner.accessToken(), senderTeam, "2026-09-12");
        String recipientSlotId = publishAndGetSlotId(recipientOwner.accessToken(), recipientTeam, "2026-09-12");

        String sendBody = """
                {
                  "senderTeamId": "%s",
                  "recipientTeamId": "%s",
                  "senderSlotId": "%s",
                  "recipientSlotId": "%s",
                  "date": "2026-09-12",
                  "startTime": "10:00:00",
                  "endTime": "12:00:00",
                  "homeTeamId": "%s",
                  "costShare": "SPLIT",
                  "refereeArrangement": "NONE",
                  "message": "Fancy a friendly?"
                }
                """.formatted(senderTeam, recipientTeam, senderSlotId, recipientSlotId, senderTeam);

        mockMvc.perform(post("/api/v1/friendly-requests")
                        .header("Authorization", "Bearer " + senderOwner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody))
                .andExpect(status().isOk());

        return senderOwner;
    }

    @Test
    @DisplayName("mark-all-read clears the unread count without deleting anything")
    void markAllReadClearsUnreadCount() throws Exception {
        TestAccount recipientOwner = registerAccount("Notif MarkAll Owner");
        String recipientTeam = createTeam(recipientOwner.accessToken(), "Notif FC MarkAll", 56.014582, -3.790261);
        sendAFriendlyRequestTo(recipientOwner, recipientTeam);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("clear-all deletes every notification for that user, and only that user")
    void clearAllDeletesOnlyTheCallersNotifications() throws Exception {
        TestAccount recipientOwner = registerAccount("Notif ClearAll Owner");
        String recipientTeam = createTeam(recipientOwner.accessToken(), "Notif FC ClearAll", 56.014582, -3.790261);
        sendAFriendlyRequestTo(recipientOwner, recipientTeam);

        TestAccount otherOwner = registerAccount("Notif Bystander Owner");
        String otherTeam = createTeam(otherOwner.accessToken(), "Notif FC Bystander", 56.014582, -3.790261);
        sendAFriendlyRequestTo(otherOwner, otherTeam);

        mockMvc.perform(delete("/api/v1/notifications")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + recipientOwner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // The bystander's own notification is untouched by someone else's clear-all.
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + otherOwner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
