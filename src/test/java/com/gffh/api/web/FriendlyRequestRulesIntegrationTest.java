package com.gffh.api.web;

import com.gffh.api.domain.Fixture;
import com.gffh.api.domain.FriendlyRequest;
import com.gffh.api.domain.RequestStatus;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static FriendlyRequest withStatus(FriendlyRequest fr, RequestStatus status) {
        return new FriendlyRequest(fr.id(), fr.senderTeamId(), fr.recipientTeamId(), fr.senderSlotId(),
                fr.recipientSlotId(), status, fr.date(), fr.startTime(), fr.endTime(), fr.venueId(),
                fr.homeTeamId(), fr.costShare(), fr.refereeArrangement(), fr.message(), fr.createdByUserId(),
                fr.createdAt(), fr.updatedAt(), fr.actionReason(), fr.proposedStartTime(), fr.proposedEndTime(),
                fr.proposedVenueId());
    }
}
