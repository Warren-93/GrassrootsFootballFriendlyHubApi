package com.gffh.api.controller;

import com.gffh.api.model.Fixture;
import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.AvailabilityStatus;
import com.gffh.api.model.enums.FixtureStatus;
import com.gffh.api.model.enums.RequestStatus;
import com.gffh.api.repository.AvailabilityRepository;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.service.AdminTeamService;
import com.gffh.api.service.FriendlyRequestService;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The rules a friendly request is held to beyond the state machine itself:
 * what a send or a counter-proposal may contain, and what happens to a
 * request whose date goes by.
 */
class FriendlyRequestRulesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FriendlyRequestRepository requests;

    @Autowired
    private FixtureRepository fixtures;

    @Autowired
    private AvailabilityRepository availability;

    @Autowired
    private AdminTeamService adminTeams;

    @Autowired
    private FriendlyRequestService friendlyRequestService;

    /** Two ready teams, each with a 10:00-12:00 slot on the same day. */
    private record Pair(TestAccount sender, String senderTeam, String senderSlot,
                        TestAccount recipient, String recipientTeam, String recipientSlot, String date) {}

    private Pair readyPair(String name, int daysAhead) throws Exception {
        TestAccount sender = registerAccount(name + " Sender");
        TestAccount recipient = registerAccount(name + " Recipient");
        verifyEmail(sender);
        String senderTeam = createTeam(sender.accessToken(), name + " FC Sender", 56.014582, -3.790261);
        String recipientTeam = createTeam(recipient.accessToken(), name + " FC Recipient", 56.014582, -3.790261);
        makeTeamReadyForRequests(sender.accessToken(), senderTeam);
        makeTeamReadyForRequests(recipient.accessToken(), recipientTeam);
        String date = daysFromNow(daysAhead);
        return new Pair(sender, senderTeam, slot(sender.accessToken(), senderTeam, date),
                recipient, recipientTeam, slot(recipient.accessToken(), recipientTeam, date), date);
    }

    private String slot(String accessToken, String teamId, String isoDate) throws Exception {
        String response = mockMvc.perform(post("/api/v1/teams/" + teamId + "/availability")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "date": "%s", "startTime": "10:00:00", "endTime": "12:00:00", "homeAwayPreference": "EITHER" }
                                """.formatted(isoDate)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private ResultActions send(Pair p, String date, String start, String end) throws Exception {
        return mockMvc.perform(post("/api/v1/friendly-requests")
                .header("Authorization", "Bearer " + p.sender().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "senderTeamId": "%s", "recipientTeamId": "%s",
                          "senderSlotId": "%s", "recipientSlotId": "%s",
                          "date": "%s", "startTime": "%s", "endTime": "%s",
                          "homeTeamId": "%s", "costShare": "SPLIT", "refereeArrangement": "NONE"
                        }
                        """.formatted(p.senderTeam(), p.recipientTeam(), p.senderSlot(), p.recipientSlot(),
                        date, start, end, p.senderTeam())));
    }

    private String sendValid(Pair p) throws Exception {
        String response = send(p, p.date(), "10:00:00", "12:00:00")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private ResultActions act(TestAccount account, String requestId, String action, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/" + action)
                .header("Authorization", "Bearer " + account.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : body));
    }

    @Test
    @DisplayName("a counter-proposal that ends before it starts is a 400, not a fixture with start after end")
    void suggestChangesRejectsBackwardsTimes() throws Exception {
        Pair p = readyPair("Rules Backwards", 30);
        String requestId = sendValid(p);

        act(p.recipient(), requestId, "suggestChanges", """
                { "proposedStartTime": "16:00:00", "proposedEndTime": "14:00:00" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TIME_RANGE"));

        // Only the start moved, past the existing end.
        act(p.recipient(), requestId, "suggestChanges", """
                { "proposedStartTime": "13:00:00" }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TIME_RANGE"));
    }

    @Test
    @DisplayName("a status change only lands if nobody moved the request on first")
    void statusChangesAreConditional() throws Exception {
        Pair p = readyPair("Rules Race", 31);
        String requestId = sendValid(p);
        FriendlyRequest read = requests.findById(requestId).orElseThrow();

        act(p.recipient(), requestId, "decline", null).andExpect(status().isOk());

        // A stale read of SENT can't be turned into an acceptance any more.
        FriendlyRequest staleAccept = withStatus(read, RequestStatus.ACCEPTED);
        assertThat(requests.saveIfStatus(staleAccept, RequestStatus.SENT)).isEmpty();
        assertThat(requests.findById(requestId).orElseThrow().status()).isEqualTo(RequestStatus.DECLINED);

        act(p.recipient(), requestId, "accept", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_TRANSITION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("a request can never have two fixtures")
    void oneFixturePerRequest() throws Exception {
        Pair p = readyPair("Rules One Fixture", 32);
        String requestId = sendValid(p);
        act(p.recipient(), requestId, "accept", null).andExpect(status().isOk());
        Fixture existing = fixtures.findByTeamId(p.senderTeam()).get(0);

        assertThatThrownBy(() -> fixtures.save(new Fixture(null, requestId, existing.homeTeamId(),
                existing.awayTeamId(), existing.date(), existing.startTime(), existing.endTime(), existing.venueId(),
                existing.status(), existing.costShare(), existing.refereeArrangement(), existing.createdAt())))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("blocking a team cancels the fixture with it properly and tells them as a normal cancellation")
    void blockCancelsFixtureAndNotifies() throws Exception {
        Pair p = readyPair("Rules Block", 33);
        String requestId = sendValid(p);
        act(p.recipient(), requestId, "accept", null).andExpect(status().isOk());

        block(p.recipient(), p.recipientTeam(), p.senderTeam()).andExpect(status().isOk());
        block(p.recipient(), p.recipientTeam(), p.senderTeam()).andExpect(status().isOk());

        assertThat(requests.findById(requestId).orElseThrow().status()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(fixtures.findByTeamId(p.senderTeam()).get(0).status()).isEqualTo(FixtureStatus.CANCELLED);
        assertThat(availability.findById(p.senderSlot()).orElseThrow().status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + p.sender().accessToken()))
                .andExpect(jsonPath("$[*].type", hasItem("FIXTURE_CANCELLED")));
        mockMvc.perform(get("/api/v1/teams/" + p.recipientTeam() + "/blocks")
                        .header("Authorization", "Bearer " + p.recipient().accessToken()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("a team can't block itself or a team that doesn't exist")
    void blockRejectsSelfAndUnknownTeams() throws Exception {
        TestAccount owner = registerAccount("Rules Block Self");
        String team = createTeam(owner.accessToken(), "Rules Block Self FC", 56.014582, -3.790261);

        block(owner, team, team)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CANNOT_BLOCK_OWN_TEAM"));
        block(owner, team, "no-such-team").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("suspending a team cancels its open requests, frees the other team's slot and tells them")
    void teamSuspensionCancelsProperly() throws Exception {
        Pair p = readyPair("Rules Suspend", 34);
        String requestId = sendValid(p);

        adminTeams.suspend("test-admin", p.senderTeam(), "Testing");

        assertThat(requests.findById(requestId).orElseThrow().status()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(availability.findById(p.recipientSlot()).orElseThrow().status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + p.recipient().accessToken()))
                .andExpect(jsonPath("$[*].type", hasItem("REQUEST_WITHDRAWN")));
    }

    @Test
    @DisplayName("a request's date and times must fall within both teams' slots")
    void sendMustMatchTheSlots() throws Exception {
        Pair p = readyPair("Rules Slot Match", 35);

        send(p, daysFromNow(36), "10:00:00", "12:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FRIENDLY_REQUEST_NOT_ALLOWED"));
        send(p, p.date(), "11:00:00", "13:00:00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("FRIENDLY_REQUEST_NOT_ALLOWED"));
        send(p, p.date(), "11:00:00", "10:30:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TIME_RANGE"));

        send(p, p.date(), "10:30:00", "12:00:00").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a suspended or archived team can't send or receive requests")
    void suspendedOrArchivedTeamsCannotTakePart() throws Exception {
        Pair p = readyPair("Rules Inactive", 37);

        setSuspendedAndArchived(p.recipientTeam(), true, false);
        send(p, p.date(), "10:00:00", "12:00:00").andExpect(status().isNotFound());

        setSuspendedAndArchived(p.recipientTeam(), false, false);
        setSuspendedAndArchived(p.senderTeam(), false, true);
        send(p, p.date(), "10:00:00", "12:00:00")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("a request whose date has passed can't be accepted, and expires so a new one can be sent")
    void pastDatedRequestsExpire() throws Exception {
        Pair p = readyPair("Rules Expiry", 38);
        String requestId = sendValid(p);
        FriendlyRequest sent = requests.findById(requestId).orElseThrow();
        LocalDate yesterday = LocalDate.now(FriendlyRequestService.FIXTURE_ZONE).minusDays(1);
        requests.save(new FriendlyRequest(sent.id(), sent.senderTeamId(), sent.recipientTeamId(),
                sent.senderSlotId(), sent.recipientSlotId(), sent.status(), yesterday, sent.startTime(),
                sent.endTime(), sent.venueId(), sent.homeTeamId(), sent.costShare(), sent.refereeArrangement(),
                sent.message(), sent.createdByUserId(), sent.createdAt(), sent.updatedAt(), null, null, null, null));

        act(p.recipient(), requestId, "accept", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_DATE_PASSED"));
        act(p.recipient(), requestId, "suggestChanges", """
                { "proposedStartTime": "10:30:00" }
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_DATE_PASSED"));

        friendlyRequestService.expirePastDue(LocalDate.now(FriendlyRequestService.FIXTURE_ZONE));

        assertThat(requests.findById(requestId).orElseThrow().status()).isEqualTo(RequestStatus.CANCELLED);
        assertThat(availability.findById(p.senderSlot()).orElseThrow().status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(availability.findById(p.recipientSlot()).orElseThrow().status()).isEqualTo(AvailabilityStatus.AVAILABLE);
        // No longer open, so the two teams can arrange another.
        sendValid(p);
    }

    private void setSuspendedAndArchived(String teamId, boolean suspended, boolean archived) {
        Team t = teamRepository.findById(teamId).orElseThrow();
        teamRepository.save(new Team(t.id(), t.clubId(), t.name(), t.clubName(), t.badgeUrl(), t.ageGroup(),
                t.gender(), t.format(), t.abilityLevel(), t.league(), t.postcode(), t.location(),
                t.travelRadiusMiles(), t.homeAwayPreference(), t.managerName(), t.contactPhone(), t.description(),
                t.verification(), t.defaultVenueId(), t.firstFixtureConfirmedAt(), t.createdAt(), t.updatedAt(),
                suspended, archived, t.searchVisible(), t.shareContactDetails()));
    }

    private ResultActions block(TestAccount account, String teamId, String blockedTeamId) throws Exception {
        return mockMvc.perform(post("/api/v1/teams/" + teamId + "/blocks")
                .header("Authorization", "Bearer " + account.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "blockedTeamId": "%s" }
                        """.formatted(blockedTeamId)));
    }

    private static FriendlyRequest withStatus(FriendlyRequest fr, RequestStatus status) {
        return new FriendlyRequest(fr.id(), fr.senderTeamId(), fr.recipientTeamId(), fr.senderSlotId(),
                fr.recipientSlotId(), status, fr.date(), fr.startTime(), fr.endTime(), fr.venueId(),
                fr.homeTeamId(), fr.costShare(), fr.refereeArrangement(), fr.message(), fr.createdByUserId(),
                fr.createdAt(), fr.updatedAt(), fr.actionReason(), fr.proposedStartTime(), fr.proposedEndTime(),
                fr.proposedVenueId());
    }
}
