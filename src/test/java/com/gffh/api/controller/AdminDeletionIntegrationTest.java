package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.model.enums.AdminRole;
import com.gffh.api.repository.PlatformAdminRepository;
import com.gffh.api.security.AdminJwtService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Permanent deletes: an admin asks, the super admin approves with their
 * password, and the user or team goes with everything tied to it - while
 * the other team in a confirmed fixture is told it was cancelled.
 */
class AdminDeletionIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass123!";

    @Autowired private AdminJwtService adminJwtService;
    @Autowired private PlatformAdminRepository admins;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("deleting a club's only member deletes the club, its team and their data, and tells the other team")
    void soleMemberTakesClubWithThem() throws Exception {
        PlatformAdmin admin = newAdmin(AdminRole.ADMIN);
        PlatformAdmin superAdmin = newAdmin(AdminRole.SUPER_ADMIN);

        TestAccount doomed = registerAccount("Doomed Manager");
        TestAccount other = registerAccount("Other Manager");
        verifyEmail(doomed);
        String doomedTeam = createTeam(doomed.accessToken(), "Deletion FC Doomed", 56.014582, -3.790261);
        String otherTeam = createTeam(other.accessToken(), "Deletion FC Other", 56.014582, -3.790261);
        makeTeamReadyForRequests(doomed.accessToken(), doomedTeam);
        makeTeamReadyForRequests(other.accessToken(), otherTeam);
        String doomedClub = teamRepository.findById(doomedTeam).orElseThrow().clubId();
        String otherSlot = confirmFixture(doomed, doomedTeam, other, otherTeam);

        JsonNode preview = json(mockMvc.perform(get("/api/v1/admin/deletions/preview")
                        .param("targetType", "USER").param("targetId", doomed.userId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        assertEquals("Deletion FC Doomed", preview.get("teamsDeleted").get(0).get("name").asText());
        assertEquals(1, preview.get("clubsDeleted").size());
        assertTrue(preview.get("otherTeamsAffected").get(0).asText().startsWith("Deletion FC Other - confirmed fixture"));
        assertTrue(preview.get("records").toString().contains("Fixtures"));

        String requestId = requestDeletion(admin, "USER", doomed.userId());

        // An ordinary admin can't approve, even their own request.
        approve(admin, requestId, ADMIN_PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("SUPER_ADMIN_ONLY"));
        // The super admin must get their password right; nothing is deleted until then.
        approve(superAdmin, requestId, "wrong-password").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REAUTH_FAILED"));
        assertEquals(1, count("users", "_id", doomed.userId()));

        approve(superAdmin, requestId, ADMIN_PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.decidedByEmail").value(superAdmin.email()));

        assertEquals(0, count("users", "_id", doomed.userId()));
        assertEquals(0, count("teams", "_id", doomedTeam));
        assertEquals(0, count("clubs", "_id", doomedClub));
        assertEquals(0, count("venues", "clubId", doomedClub));
        assertEquals(0, count("memberships", "userId", doomed.userId()));
        assertEquals(0, count("availability", "teamId", doomedTeam));
        assertEquals(0, count("fixtures", "homeTeamId", doomedTeam) + count("fixtures", "awayTeamId", doomedTeam));
        assertEquals(0, count("friendlyRequests", "senderTeamId", doomedTeam));
        assertEquals(0, count("refreshTokens", "userId", doomed.userId()));

        // The other team keeps its account and team, gets its slot back, and is told.
        assertEquals(1, count("teams", "_id", otherTeam));
        assertEquals("AVAILABLE", mongoTemplate.findById(otherSlot, org.bson.Document.class, "availability").getString("status"));
        JsonNode notifications = json(mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isOk()));
        assertTrue(notifications.toString().contains("Fixture cancelled"));

        assertEquals(1, mongoTemplate.count(new Query(Criteria.where("action").is("USER_DELETED")
                .and("targetId").is(doomed.userId())), "audit_log"));
        // The request is kept as the record.
        approve(superAdmin, requestId, ADMIN_PASSWORD).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("the fixture list finds fixtures by part of a team name and by date")
    void fixtureList() throws Exception {
        PlatformAdmin admin = newAdmin(AdminRole.ADMIN);
        TestAccount home = registerAccount("List Home");
        TestAccount away = registerAccount("List Away");
        verifyEmail(home);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String homeTeam = createTeam(home.accessToken(), "Listing " + unique + " Home", 56.0, -3.7);
        String awayTeam = createTeam(away.accessToken(), "Listing " + unique + " Away", 56.0, -3.7);
        makeTeamReadyForRequests(home.accessToken(), homeTeam);
        makeTeamReadyForRequests(away.accessToken(), awayTeam);
        confirmFixture(home, homeTeam, away, awayTeam);

        JsonNode byTeam = json(mockMvc.perform(get("/api/v1/admin/fixtures").param("teamQuery", unique.toUpperCase())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        assertEquals(1, byTeam.size());
        assertEquals("Listing " + unique + " Home", byTeam.get(0).get("homeTeamName").asText());
        assertEquals("CONFIRMED", byTeam.get(0).get("status").asText());

        JsonNode wrongDate = json(mockMvc.perform(get("/api/v1/admin/fixtures").param("teamQuery", unique)
                        .param("from", daysFromNow(11)).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        assertEquals(0, wrongDate.size());
        JsonNode onDate = json(mockMvc.perform(get("/api/v1/admin/fixtures").param("teamQuery", unique)
                        .param("from", daysFromNow(10)).param("to", daysFromNow(10)).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        assertEquals(1, onDate.size());
    }

    @Test
    @DisplayName("deleting a team keeps its club when the club still has members, and keeps the users")
    void teamDeleteKeepsClubWithMembers() throws Exception {
        PlatformAdmin superAdmin = newAdmin(AdminRole.SUPER_ADMIN);
        TestAccount owner = registerAccount("Team Owner");
        String team = createTeam(owner.accessToken(), "Deletion FC Team Only", 56.0, -3.7);
        String club = teamRepository.findById(team).orElseThrow().clubId();
        publishAvailability(owner.accessToken(), team, daysFromNow(12));

        JsonNode preview = json(mockMvc.perform(get("/api/v1/admin/deletions/preview")
                        .param("targetType", "TEAM").param("targetId", team)
                        .header("Authorization", bearer(superAdmin)))
                .andExpect(status().isOk()));
        assertEquals(0, preview.get("clubsDeleted").size());

        String requestId = requestDeletion(superAdmin, "TEAM", team);
        approve(superAdmin, requestId, ADMIN_PASSWORD).andExpect(status().isOk());

        assertEquals(0, count("teams", "_id", team));
        assertEquals(0, count("availability", "teamId", team));
        assertEquals(0, count("memberships", "teamId", team));
        assertEquals(1, count("clubs", "_id", club));
        assertEquals(1, count("users", "_id", owner.userId()));
    }

    @Test
    @DisplayName("a deleted user's club stays when someone else is still in it, with a warning")
    void sharedClubStays() throws Exception {
        PlatformAdmin admin = newAdmin(AdminRole.ADMIN);
        TestAccount owner = registerAccount("Shared Owner");
        String team = createTeam(owner.accessToken(), "Deletion FC Shared", 56.0, -3.7);
        String club = teamRepository.findById(team).orElseThrow().clubId();
        TestAccount colleague = registerAccount("Shared Colleague");
        mongoTemplate.insert(new org.bson.Document("_id", UUID.randomUUID().toString())
                .append("userId", colleague.userId()).append("teamId", team)
                .append("role", "TEAM_MANAGER").append("createdAt", java.util.Date.from(Instant.now())), "memberships");

        JsonNode preview = json(mockMvc.perform(get("/api/v1/admin/deletions/preview")
                        .param("targetType", "USER").param("targetId", owner.userId())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        assertEquals(0, preview.get("clubsDeleted").size());
        assertEquals(0, preview.get("teamsDeleted").size());
        assertTrue(preview.get("warnings").toString().contains("no club admin"));
        assertEquals(1, count("clubs", "_id", club));
    }

    @Test
    @DisplayName("asking twice for the same delete returns the request already waiting; it can be withdrawn")
    void oneRequestPerTarget() throws Exception {
        PlatformAdmin admin = newAdmin(AdminRole.ADMIN);
        TestAccount user = registerAccount("Withdrawn Target");
        String first = requestDeletion(admin, "USER", user.userId());
        assertEquals(first, requestDeletion(admin, "USER", user.userId()));

        mockMvc.perform(post("/api/v1/admin/deletions/" + first + "/cancel").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(1, count("users", "_id", user.userId()));
    }

    @Test
    @DisplayName("only the super admin manages admin accounts; /me says which level you are")
    void adminAccounts() throws Exception {
        PlatformAdmin admin = newAdmin(AdminRole.ADMIN);
        PlatformAdmin superAdmin = newAdmin(AdminRole.SUPER_ADMIN);

        mockMvc.perform(get("/api/v1/admin/admins/me").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/api/v1/admin/admins").header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden());

        String email = "new-admin-" + UUID.randomUUID() + "@example.com";
        JsonNode created = json(mockMvc.perform(post("/api/v1/admin/admins").header("Authorization", bearer(superAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"%s\", \"password\": \"LongEnough123\" }".formatted(email)))
                .andExpect(status().isOk()));
        assertEquals("ADMIN", created.get("admin").get("role").asText());

        mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"email\": \"%s\", \"password\": \"LongEnough123\" }".formatted(email)))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/admins/" + superAdmin.id()).header("Authorization", bearer(superAdmin)))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/admin/admins/" + created.get("admin").get("id").asText())
                        .header("Authorization", bearer(superAdmin)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("user and team detail pages carry dates and a timeline")
    void detailTimelines() throws Exception {
        PlatformAdmin admin = newAdmin(AdminRole.ADMIN);
        TestAccount owner = registerAccount("Timeline Owner");
        String team = createTeam(owner.accessToken(), "Deletion FC Timeline", 56.0, -3.7);

        JsonNode user = json(mockMvc.perform(get("/api/v1/admin/users/" + owner.userId() + "/detail")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        assertNotNull(user.get("user").get("createdAt").asText(null));
        assertNotNull(user.get("user").get("lastActiveAt").asText(null));
        String timeline = user.get("timeline").toString();
        assertTrue(timeline.contains("ACCOUNT_CREATED"));
        assertTrue(timeline.contains("Joined Deletion FC Timeline"));

        JsonNode teamDetail = json(mockMvc.perform(get("/api/v1/admin/teams/" + team + "/detail")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()));
        assertTrue(teamDetail.get("timeline").toString().contains("TEAM_CREATED"));
        assertEquals("Timeline Owner", teamDetail.get("members").get(0).get("displayName").asText());
    }

    // ---- helpers ------------------------------------------------------------

    private PlatformAdmin newAdmin(AdminRole role) {
        return admins.save(new PlatformAdmin(null, "it-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode(ADMIN_PASSWORD), "SECRET", Instant.now(), null, role));
    }

    private String bearer(PlatformAdmin admin) {
        return "Bearer " + adminJwtService.issueSessionToken(admin.id(), admin.email());
    }

    private String requestDeletion(PlatformAdmin admin, String type, String id) throws Exception {
        return json(mockMvc.perform(post("/api/v1/admin/deletions").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"targetType\": \"%s\", \"targetId\": \"%s\", \"reason\": \"Test clean-up\" }"
                                .formatted(type, id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions approve(PlatformAdmin admin, String requestId,
                                                                       String password) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/deletions/" + requestId + "/approve")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"password\": \"%s\" }".formatted(password)));
    }

    /** Sends and accepts a friendly between the two teams; returns the recipient's slot id. */
    private String confirmFixture(TestAccount sender, String senderTeam, TestAccount recipient, String recipientTeam)
            throws Exception {
        String date = daysFromNow(10);
        String senderSlot = slot(sender, senderTeam, date);
        String recipientSlot = slot(recipient, recipientTeam, date);
        String requestId = json(mockMvc.perform(post("/api/v1/friendly-requests")
                        .header("Authorization", "Bearer " + sender.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "senderTeamId": "%s", "recipientTeamId": "%s", "senderSlotId": "%s",
                                  "recipientSlotId": "%s", "date": "%s", "startTime": "10:00:00", "endTime": "12:00:00",
                                  "homeTeamId": "%s", "costShare": "SPLIT", "refereeArrangement": "NONE" }
                                """.formatted(senderTeam, recipientTeam, senderSlot, recipientSlot, date, senderTeam)))
                .andExpect(status().isOk())).get("id").asText();
        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/accept")
                        .header("Authorization", "Bearer " + recipient.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        return recipientSlot;
    }

    private String slot(TestAccount owner, String teamId, String date) throws Exception {
        return json(mockMvc.perform(post("/api/v1/teams/" + teamId + "/availability")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"date\": \"%s\", \"startTime\": \"10:00:00\", \"endTime\": \"12:00:00\", \"homeAwayPreference\": \"EITHER\" }"
                                .formatted(date)))
                .andExpect(status().isOk())).get("id").asText();
    }

    private long count(String collection, String field, String value) {
        return mongoTemplate.count(new Query(Criteria.where(field).is(value)), collection);
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
