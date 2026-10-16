package com.gffh.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.service.AdminJwtService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CLUB_ADMIN carries club-wide powers, so only a club admin can grant it, or
 * change or remove someone who has it. A team manager still runs the squad's
 * players and other team managers as before, and anything touching a club
 * admin gets them 403 CLUB_ADMIN_ONLY and changes nothing.
 */
class ClubAdminMembersIntegrationTest extends AbstractIntegrationTest {

    private static final String CLUB_ADMIN_ONLY_MESSAGE = "Only a club admin can add, change or remove club admins.";

    @Autowired
    private AdminJwtService adminJwtService;

    @Test
    @DisplayName("a team manager can't add, invite, promote to, demote or remove a club admin, and nothing changes")
    void teamManagersCannotTouchClubAdmins() throws Exception {
        TestAccount owner = verifiedAccount("Admin Guard Owner");
        String teamId = createTeam(owner.accessToken(), "Admin Guard FC", 56.014582, -3.790261);
        TestAccount manager = verifiedAccount("Admin Guard Manager");
        String managerMembership = membershipId(addMember(owner, teamId, manager.email(), "TEAM_MANAGER")
                .andExpect(status().isOk()));
        // Running a club of their own must not count towards this one.
        String managerOwnTeamId = createTeam(manager.accessToken(), "Admin Guard Elsewhere FC", 56.014582, -3.790261);
        // A second club admin, so LAST_CLUB_ADMIN can't be what refuses the demotions below.
        TestAccount secondAdmin = registerAccount("Admin Guard Second Admin");
        String secondAdminMembership = membershipId(addMember(owner, teamId, secondAdmin.email(), "CLUB_ADMIN")
                .andExpect(status().isOk()));
        TestAccount player = registerAccount("Admin Guard Player");
        String playerMembership = membershipId(addMember(owner, teamId, player.email(), "USER")
                .andExpect(status().isOk()));
        String ownerMembership = findMember(owner, teamId, owner.email()).get("membershipId").asText();
        TestAccount bystander = registerAccount("Admin Guard Bystander");

        expectClubAdminOnly(addMember(manager, teamId, bystander.email(), "CLUB_ADMIN"));
        expectClubAdminOnly(addMember(manager, teamId, newEmail(), "CLUB_ADMIN"));
        expectClubAdminOnly(changeRole(manager, teamId, playerMembership, "CLUB_ADMIN"));
        expectClubAdminOnly(changeRole(manager, teamId, managerMembership, "CLUB_ADMIN"));
        expectClubAdminOnly(changeRole(manager, teamId, secondAdminMembership, "TEAM_MANAGER"));
        expectClubAdminOnly(changeRole(manager, teamId, ownerMembership, "USER"));
        expectClubAdminOnly(removeMember(manager, teamId, secondAdminMembership));
        expectClubAdminOnly(removeMember(manager, teamId, ownerMembership));

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        assertRole(owner, teamId, owner.email(), "CLUB_ADMIN", "CLUB");
        assertRole(owner, teamId, secondAdmin.email(), "CLUB_ADMIN", "CLUB");
        assertRole(owner, teamId, manager.email(), "TEAM_MANAGER", "TEAM");
        assertRole(owner, teamId, player.email(), "USER", "TEAM");
        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members/invites")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/teams/mine")
                        .header("Authorization", "Bearer " + manager.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].canManageClub", teamId).value(false))
                .andExpect(jsonPath("$[?(@.id == '%s')].canManageClub", managerOwnTeamId).value(true));
    }

    @Test
    @DisplayName("a team manager can still add, invite, change and remove players and other team managers")
    void teamManagersStillRunTheSquad() throws Exception {
        TestAccount owner = verifiedAccount("Squad Owner");
        String teamId = createTeam(owner.accessToken(), "Squad FC", 56.014582, -3.790261);
        TestAccount manager = verifiedAccount("Squad Manager");
        addMember(owner, teamId, manager.email(), "TEAM_MANAGER").andExpect(status().isOk());

        TestAccount player = registerAccount("Squad Player");
        String playerMembership = membershipId(addMember(manager, teamId, player.email(), "USER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER")));
        changeRole(manager, teamId, playerMembership, "TEAM_MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEAM_MANAGER"))
                .andExpect(jsonPath("$.scope").value("TEAM"));
        changeRole(manager, teamId, playerMembership, "USER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
        removeMember(manager, teamId, playerMembership).andExpect(status().isNoContent());
        assertNull(findMember(owner, teamId, player.email()));

        TestAccount coach = registerAccount("Squad Coach");
        addMember(manager, teamId, coach.email(), "TEAM_MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEAM_MANAGER"));

        String email = newEmail();
        addMember(manager, teamId, email, "USER")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.invite.role").value("USER"));
        String inviteId = objectMapper.readTree(addMember(manager, teamId, email, "TEAM_MANAGER")
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.invite.role").value("TEAM_MANAGER"))
                        .andReturn().getResponse().getContentAsString())
                .get("invite").get("id").asText();
        mockMvc.perform(delete("/api/v1/teams/" + teamId + "/members/invites/" + inviteId)
                        .header("Authorization", "Bearer " + manager.accessToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a club admin can still add, invite, promote, demote and remove club admins, but not the last one")
    void clubAdminsStillManageClubAdmins() throws Exception {
        TestAccount owner = verifiedAccount("Club Admin Owner");
        String teamId = createTeam(owner.accessToken(), "Club Admin FC", 56.014582, -3.790261);
        TestAccount manager = registerAccount("Club Admin Manager");
        String managerMembership = membershipId(addMember(owner, teamId, manager.email(), "TEAM_MANAGER")
                .andExpect(status().isOk()));
        String ownerMembership = findMember(owner, teamId, owner.email()).get("membershipId").asText();

        changeRole(owner, teamId, ownerMembership, "TEAM_MANAGER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_CLUB_ADMIN"));
        removeMember(owner, teamId, ownerMembership)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_CLUB_ADMIN"));

        TestAccount admin = registerAccount("Club Admin Added");
        String adminMembership = membershipId(addMember(owner, teamId, admin.email(), "CLUB_ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLUB_ADMIN"))
                .andExpect(jsonPath("$.scope").value("CLUB")));
        changeRole(owner, teamId, managerMembership, "CLUB_ADMIN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLUB_ADMIN"))
                .andExpect(jsonPath("$.scope").value("CLUB"));
        changeRole(owner, teamId, managerMembership, "TEAM_MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEAM_MANAGER"))
                .andExpect(jsonPath("$.scope").value("TEAM"));

        // An invite already carrying CLUB_ADMIN still joins as one once the address is proved.
        String email = newEmail();
        addMember(owner, teamId, email, "CLUB_ADMIN")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.invite.role").value("CLUB_ADMIN"));
        TestAccount invitee = registerAccount("Club Admin Invitee", email);
        adminVerify(invitee);
        assertRole(owner, teamId, email, "CLUB_ADMIN", "CLUB");

        // That club admin has no membership of the team itself, and can still remove another.
        removeMember(invitee, teamId, adminMembership).andExpect(status().isNoContent());
        assertNull(findMember(owner, teamId, admin.email()));
    }

    // ---- Helpers --------------------------------------------------------

    private void expectClubAdminOnly(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CLUB_ADMIN_ONLY"))
                .andExpect(jsonPath("$.error.message").value(CLUB_ADMIN_ONLY_MESSAGE));
    }

    private ResultActions addMember(TestAccount actor, String teamId, String email, String role) throws Exception {
        return mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                .header("Authorization", "Bearer " + actor.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "role": "%s" }
                        """.formatted(email, role)));
    }

    private ResultActions changeRole(TestAccount actor, String teamId, String membershipId, String role)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/teams/" + teamId + "/members/" + membershipId)
                .header("Authorization", "Bearer " + actor.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "role": "%s" }
                        """.formatted(role)));
    }

    private ResultActions removeMember(TestAccount actor, String teamId, String membershipId) throws Exception {
        return mockMvc.perform(delete("/api/v1/teams/" + teamId + "/members/" + membershipId)
                .header("Authorization", "Bearer " + actor.accessToken()));
    }

    private String membershipId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("membershipId").asText();
    }

    private void assertRole(TestAccount viewer, String teamId, String email, String role, String scope)
            throws Exception {
        JsonNode member = findMember(viewer, teamId, email);
        assertNotNull(member, email + " should still be a member");
        assertEquals(role, member.get("role").asText());
        assertEquals(scope, member.get("scope").asText());
    }

    private JsonNode findMember(TestAccount viewer, String teamId, String email) throws Exception {
        JsonNode members = objectMapper.readTree(mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + viewer.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return StreamSupport.stream(members.spliterator(), false)
                .filter(m -> email.equals(m.path("email").asText()))
                .findFirst().orElse(null);
    }

    private TestAccount verifiedAccount(String displayName) throws Exception {
        TestAccount account = registerAccount(displayName);
        verifyEmail(account);
        return account;
    }

    /** An admin's "verify email now", which proves the address in tests where nothing is emailed. */
    private void adminVerify(TestAccount account) throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + account.userId() + "/verify-email")
                        .header("Authorization", "Bearer " + adminJwtService.issueSessionToken(
                                "it-admin-" + UUID.randomUUID(), "admin@example.com")))
                .andExpect(status().isOk());
    }

    private static String newEmail() {
        return "invitee_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.com";
    }
}
