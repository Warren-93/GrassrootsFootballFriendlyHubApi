package com.gffh.api.web;

import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Read-only reach below management: a member who joined with a code sees the
 * team's fixtures without the managers' contact details, and a team manager
 * sees the club's venues to pick one from - neither can change anything, and
 * GET /teams/mine tells clients so they can hide what would be refused.
 */
class TeamMemberAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("a member who joined with a code sees the team's fixtures without contact details, and can't cancel")
    void membersSeeFixturesWithoutContactDetails() throws Exception {
        TestAccount home = registerAccount("Reach Home Owner");
        TestAccount away = registerAccount("Reach Away Owner");
        verifyEmail(home);
        String homeTeam = createTeam(home.accessToken(), "Reach FC Home", 56.014582, -3.790261);
        String awayTeam = createTeam(away.accessToken(), "Reach FC Away", 56.014582, -3.790261);
        makeTeamReadyForRequests(home.accessToken(), homeTeam);
        makeTeamReadyForRequests(away.accessToken(), awayTeam);
        String fixtureId = confirmFixture(home, away, homeTeam, awayTeam, daysFromNow(15));

        TestAccount member = registerAccount("Reach Member");
        joinWithCode(member, joinCode(home, homeTeam));
        TestAccount stranger = registerAccount("Reach Stranger");

        // Managers still see each side's contact details.
        mockMvc.perform(get("/api/v1/fixtures/" + fixtureId)
                        .header("Authorization", "Bearer " + home.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam.managerName").value("Test Manager"))
                .andExpect(jsonPath("$.awayTeam.contactPhone").value("07700900000"));

        mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", homeTeam)
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fixtureId))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].homeTeam.name").value("Reach FC Home"))
                .andExpect(jsonPath("$[0].venue.name").value("Test Park"))
                .andExpect(jsonPath("$[0].homeTeam.managerName").doesNotExist())
                .andExpect(jsonPath("$[0].homeTeam.contactPhone").doesNotExist())
                .andExpect(jsonPath("$[0].awayTeam.managerName").doesNotExist())
                .andExpect(jsonPath("$[0].awayTeam.contactPhone").doesNotExist());

        mockMvc.perform(get("/api/v1/fixtures/" + fixtureId)
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.awayTeam.name").value("Reach FC Away"))
                .andExpect(jsonPath("$.homeTeam.managerName").doesNotExist())
                .andExpect(jsonPath("$.homeTeam.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.awayTeam.managerName").doesNotExist())
                .andExpect(jsonPath("$.awayTeam.contactPhone").doesNotExist());

        // Joining one team gives no reach over the other side's fixture list.
        mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", awayTeam)
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/fixtures/" + fixtureId + "/cancel")
                        .header("Authorization", "Bearer " + member.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Can't make it" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FIXTURE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", homeTeam)
                        .header("Authorization", "Bearer " + stranger.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/fixtures/" + fixtureId)
                        .header("Authorization", "Bearer " + stranger.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FIXTURE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/fixtures/" + fixtureId)
                        .header("Authorization", "Bearer " + home.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("a team manager can list the club's venues but not change them, and members and other clubs can't list them")
    void teamManagersSeeTheClubsVenues() throws Exception {
        TestAccount owner = registerAccount("Venue Reach Owner");
        String teamId = createTeam(owner.accessToken(), "Venue Reach FC", 56.014582, -3.790261);
        makeTeamReadyForRequests(owner.accessToken(), teamId);
        String clubId = objectMapper.readTree(mockMvc.perform(get("/api/v1/teams/" + teamId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("clubId").asText();

        TestAccount manager = registerAccount("Venue Reach Manager");
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "role": "TEAM_MANAGER" }
                                """.formatted(manager.email())))
                .andExpect(status().isOk());

        String venueId = objectMapper.readTree(mockMvc.perform(get("/api/v1/venues")
                        .param("clubId", clubId)
                        .header("Authorization", "Bearer " + manager.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Park"))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();

        mockMvc.perform(patch("/api/v1/venues/" + venueId)
                        .header("Authorization", "Bearer " + manager.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Renamed Park" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLUB_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/venues/" + venueId + "/default")
                        .header("Authorization", "Bearer " + manager.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLUB_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/venues/" + venueId)
                        .header("Authorization", "Bearer " + manager.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLUB_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/venues")
                        .header("Authorization", "Bearer " + manager.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clubId": "%s",
                                  "name": "Manager Park",
                                  "address": "2 Test Road",
                                  "latitude": 56.014582,
                                  "longitude": -3.790261
                                }
                                """.formatted(clubId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLUB_NOT_FOUND"));

        TestAccount member = registerAccount("Venue Reach Member");
        joinWithCode(member, joinCode(owner, teamId));
        mockMvc.perform(get("/api/v1/venues")
                        .param("clubId", clubId)
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLUB_NOT_FOUND"));

        // Managing a team in another club gives no reach over this one's venues.
        TestAccount rival = registerAccount("Venue Reach Rival");
        createTeam(rival.accessToken(), "Venue Reach Rivals", 56.014582, -3.790261);
        mockMvc.perform(get("/api/v1/venues")
                        .param("clubId", clubId)
                        .header("Authorization", "Bearer " + rival.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CLUB_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/venues")
                        .param("clubId", clubId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Test Park"));
    }

    @Test
    @DisplayName("GET /teams/mine says only the club's admin can manage the club, not its team managers or members")
    void onlyClubAdminsCanManageTheClub() throws Exception {
        TestAccount owner = registerAccount("Club Reach Owner");
        String teamId = createTeam(owner.accessToken(), "Club Reach FC", 56.014582, -3.790261);

        TestAccount manager = registerAccount("Club Reach Manager");
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "role": "TEAM_MANAGER" }
                                """.formatted(manager.email())))
                .andExpect(status().isOk());
        TestAccount member = registerAccount("Club Reach Member");
        joinWithCode(member, joinCode(owner, teamId));

        mockMvc.perform(get("/api/v1/teams/mine")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(teamId))
                .andExpect(jsonPath("$[0].canManage").value(true))
                .andExpect(jsonPath("$[0].canManageClub").value(true));
        mockMvc.perform(get("/api/v1/teams/mine")
                        .header("Authorization", "Bearer " + manager.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(teamId))
                .andExpect(jsonPath("$[0].canManage").value(true))
                .andExpect(jsonPath("$[0].canManageClub").value(false));
        mockMvc.perform(get("/api/v1/teams/mine")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(teamId))
                .andExpect(jsonPath("$[0].canManage").value(false))
                .andExpect(jsonPath("$[0].canManageClub").value(false));

        // A club admin added later, with no membership of the team itself, can manage the club too.
        TestAccount admin = registerAccount("Club Reach Admin");
        mockMvc.perform(post("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "role": "CLUB_ADMIN" }
                                """.formatted(admin.email())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teams/mine")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(teamId))
                .andExpect(jsonPath("$[0].canManage").value(true))
                .andExpect(jsonPath("$[0].canManageClub").value(true));

        // It's per team: running a club of your own says nothing about another club's team you manage.
        String ownTeamId = createTeam(manager.accessToken(), "Club Reach Own FC", 56.014582, -3.790261);
        mockMvc.perform(get("/api/v1/teams/mine")
                        .header("Authorization", "Bearer " + manager.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == '%s')].canManage", teamId).value(true))
                .andExpect(jsonPath("$[?(@.id == '%s')].canManageClub", teamId).value(false))
                .andExpect(jsonPath("$[?(@.id == '%s')].canManage", ownTeamId).value(true))
                .andExpect(jsonPath("$[?(@.id == '%s')].canManageClub", ownTeamId).value(true));

        // Only /teams/mine says; other team views leave both out.
        mockMvc.perform(get("/api/v1/teams/" + teamId)
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").doesNotExist())
                .andExpect(jsonPath("$.canManageClub").doesNotExist());
    }

    // ---- Helpers --------------------------------------------------------

    /** Sends a request from {@code home} with them at home, has {@code away} accept it, and returns the fixture's id. */
    private String confirmFixture(TestAccount home, TestAccount away, String homeTeam, String awayTeam,
                                  String isoDate) throws Exception {
        String homeSlot = slot(home.accessToken(), homeTeam, isoDate);
        String awaySlot = slot(away.accessToken(), awayTeam, isoDate);
        String requestId = id(mockMvc.perform(post("/api/v1/friendly-requests")
                        .header("Authorization", "Bearer " + home.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senderTeamId": "%s", "recipientTeamId": "%s",
                                  "senderSlotId": "%s", "recipientSlotId": "%s",
                                  "date": "%s", "startTime": "10:00:00", "endTime": "12:00:00",
                                  "homeTeamId": "%s", "costShare": "SPLIT", "refereeArrangement": "NONE"
                                }
                                """.formatted(homeTeam, awayTeam, homeSlot, awaySlot, isoDate, homeTeam)))
                .andExpect(status().isOk()));

        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/accept")
                        .header("Authorization", "Bearer " + away.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        return objectMapper.readTree(mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", homeTeam)
                        .header("Authorization", "Bearer " + home.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asText();
    }

    private String slot(String accessToken, String teamId, String isoDate) throws Exception {
        return id(mockMvc.perform(post("/api/v1/teams/" + teamId + "/availability")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "date": "%s", "startTime": "10:00:00", "endTime": "12:00:00", "homeAwayPreference": "EITHER" }
                                """.formatted(isoDate)))
                .andExpect(status().isOk()));
    }

    @Test
    @DisplayName("redeeming a join code twice leaves one membership")
    void joinCodeRedeemIsIdempotent() throws Exception {
        TestAccount owner = registerAccount("Join Twice Owner");
        String teamId = createTeam(owner.accessToken(), "Join Twice FC", 56.014582, -3.790261);
        TestAccount member = registerAccount("Join Twice Member");
        String code = joinCode(owner, teamId);

        joinWithCode(member, code);
        joinWithCode(member, code);

        mockMvc.perform(get("/api/v1/teams/" + teamId + "/members")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == '%s')]".formatted(member.userId()), hasSize(1)));
    }

    private String joinCode(TestAccount owner, String teamId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/teams/" + teamId + "/members/join-code")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("code").asText();
    }

    /** Joins as a plain member (role USER), the way a player does with the code their manager hands out. */
    private void joinWithCode(TestAccount account, String code) throws Exception {
        mockMvc.perform(post("/api/v1/teams/join")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "%s" }
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.role").value("USER"));
    }

    private String id(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }
}
