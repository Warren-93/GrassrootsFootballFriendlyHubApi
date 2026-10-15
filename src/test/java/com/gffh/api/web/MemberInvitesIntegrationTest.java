package com.gffh.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.service.AdminJwtService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SCR-PR-04's invitations: adding an address with no account invites it, and
 * the invite becomes a membership once that address is verified - never on
 * registration alone.
 */
class MemberInvitesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminJwtService adminJwtService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("an address with no account gets a 202 invite, and inviting it again refreshes the same invite")
    void invitingAnUnknownAddress() throws Exception {
        TestAccount owner = verifiedAccount("Invite Owner");
        String teamId = createTeam(owner.accessToken(), "Invite FC", 56.014582, -3.790261);
        String email = newEmail();

        String first = addMember(owner, teamId, email.toUpperCase(), "TEAM_MANAGER")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("INVITED"))
                .andExpect(jsonPath("$.invite.email").value(email))
                .andExpect(jsonPath("$.invite.role").value("TEAM_MANAGER"))
                .andExpect(jsonPath("$.invite.teamId").value(teamId))
                .andExpect(jsonPath("$.invite.teamName").value("Invite FC"))
                .andReturn().getResponse().getContentAsString();
        JsonNode invite = objectMapper.readTree(first).get("invite");
        Instant createdAt = Instant.parse(invite.get("createdAt").asText());
        Instant expiresAt = Instant.parse(invite.get("expiresAt").asText());
        assertEquals(Duration.ofDays(30), Duration.between(createdAt, expiresAt));

        addMember(owner, teamId, email, "USER")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.invite.id").value(invite.get("id").asText()))
                .andExpect(jsonPath("$.invite.role").value("USER"));

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members/invites")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(email))
                .andExpect(jsonPath("$[0].role").value("USER"));
    }

    @Test
    @DisplayName("an existing account is still added straight away with 200")
    void addingAnExistingAccount() throws Exception {
        TestAccount owner = verifiedAccount("Invite Existing Owner");
        String teamId = createTeam(owner.accessToken(), "Invite Existing FC", 56.014582, -3.790261);
        TestAccount player = registerAccount("Existing Player");

        addMember(owner, teamId, player.email(), "USER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(player.userId()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").doesNotExist());

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members/invites")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the invite becomes a membership when the address is verified, not when it registers")
    void verificationAcceptsTheInvite() throws Exception {
        TestAccount owner = verifiedAccount("Invite Verify Owner");
        String teamId = createTeam(owner.accessToken(), "Invite Verify FC", 56.014582, -3.790261);
        String email = newEmail();
        addMember(owner, teamId, email, "TEAM_MANAGER").andExpect(status().isAccepted());

        TestAccount invitee = registerAccount("Invited Manager", email);
        assertNull(findMember(owner, teamId, email), "registering alone must not join the team");

        verifyEmail(invitee);

        JsonNode member = findMember(owner, teamId, email);
        assertNotNull(member);
        assertEquals("TEAM_MANAGER", member.get("role").asText());
        assertEquals("TEAM", member.get("scope").asText());
        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members/invites")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(jsonPath("$.length()").value(0));
        // As a manager now, the invitee can manage the team themselves.
        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + invitee.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("an admin's verify-email-now accepts pending invites too")
    void adminVerificationAcceptsTheInvite() throws Exception {
        TestAccount owner = verifiedAccount("Invite Admin Owner");
        String teamId = createTeam(owner.accessToken(), "Invite Admin FC", 56.014582, -3.790261);
        String email = newEmail();
        addMember(owner, teamId, email, "USER").andExpect(status().isAccepted());
        TestAccount invitee = registerAccount("Admin Verified Player", email);

        mockMvc.perform(post("/api/v1/admin/users/" + invitee.userId() + "/verify-email")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());

        JsonNode member = findMember(owner, teamId, email);
        assertNotNull(member);
        assertEquals("USER", member.get("role").asText());
    }

    @Test
    @DisplayName("a cancelled invite is gone, and only the team's managers can see or cancel invites")
    void cancellingAnInvite() throws Exception {
        TestAccount owner = verifiedAccount("Invite Cancel Owner");
        String teamId = createTeam(owner.accessToken(), "Invite Cancel FC", 56.014582, -3.790261);
        String email = newEmail();
        String inviteId = objectMapper.readTree(addMember(owner, teamId, email, "USER")
                        .andExpect(status().isAccepted())
                        .andReturn().getResponse().getContentAsString())
                .get("invite").get("id").asText();
        TestAccount outsider = registerAccount("Invite Outsider");

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members/invites")
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/teams/" + teamId + "/members/invites/" + inviteId)
                        .header("Authorization", "Bearer " + outsider.accessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/teams/" + teamId + "/members/invites/not-an-invite")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("INVITE_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/teams/" + teamId + "/members/invites/" + inviteId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members/invites")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(jsonPath("$.length()").value(0));

        verifyEmail(registerAccount("Uninvited Player", email));
        assertNull(findMember(owner, teamId, email));
    }

    @Test
    @DisplayName("a manager must verify their own email before inviting a new address")
    void unverifiedManagersCannotInvite() throws Exception {
        TestAccount owner = registerAccount("Invite Unverified Owner");
        String teamId = createTeam(owner.accessToken(), "Invite Unverified FC", 56.014582, -3.790261);

        addMember(owner, teamId, newEmail(), "USER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("deleting an account forgets invites addressed to it")
    void accountDeletionForgetsInvites() throws Exception {
        TestAccount owner = verifiedAccount("Invite Delete Owner");
        String teamId = createTeam(owner.accessToken(), "Invite Delete FC", 56.014582, -3.790261);
        String email = newEmail();
        addMember(owner, teamId, email, "USER").andExpect(status().isAccepted());
        TestAccount invitee = registerAccount("Short-lived Player", email);

        mockMvc.perform(delete("/api/v1/me")
                        .header("Authorization", "Bearer " + invitee.accessToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members/invites")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Mongo sweeps invites away once they expire")
    void expiredInvitesAreSwept() {
        IndexInfo ttl = mongoTemplate.indexOps("memberInvites").getIndexInfo().stream()
                .filter(i -> i.isIndexForFields(List.of("expiresAt")))
                .findFirst().orElseThrow(() -> new AssertionError("no index on expiresAt"));
        assertEquals(Optional.of(Duration.ZERO), ttl.getExpireAfter());
    }

    private TestAccount verifiedAccount(String displayName) throws Exception {
        TestAccount account = registerAccount(displayName);
        verifyEmail(account);
        return account;
    }

    private ResultActions addMember(TestAccount owner, String teamId, String email, String role) throws Exception {
        return mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + owner.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "role": "%s" }
                        """.formatted(email, role)));
    }

    private JsonNode findMember(TestAccount owner, String teamId, String email) throws Exception {
        JsonNode members = objectMapper.readTree(mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return StreamSupport.stream(members.spliterator(), false)
                .filter(m -> email.equals(m.path("email").asText()))
                .findFirst().orElse(null);
    }

    private static String newEmail() {
        return "invitee_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
    }

    private String adminToken() {
        return adminJwtService.issueSessionToken("it-admin-" + UUID.randomUUID(), "admin@example.com");
    }
}
