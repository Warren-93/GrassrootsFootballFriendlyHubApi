package com.gffh.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.gffh.api.model.Fixture;
import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.enums.CostShare;
import com.gffh.api.model.enums.FixtureStatus;
import com.gffh.api.model.enums.RefereeArrangement;
import com.gffh.api.model.enums.RequestStatus;
import com.gffh.api.repository.FixtureRepository;
import com.gffh.api.repository.FriendlyRequestRepository;
import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Venues on requests and fixtures (a request with no venue is played at the
 * home team's default venue), the team names and badges that come with them,
 * and arranging a second friendly once the first is confirmed.
 */
class RequestVenueIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FriendlyRequestRepository friendlyRequestRepository;

    @Autowired
    private FixtureRepository fixtureRepository;

    @Test
    @DisplayName("a request sent without a venue gets the home team's, and a second friendly is allowed once confirmed")
    void venueFallbackTeamRefsAndSecondFriendly() throws Exception {
        Pair p = readyPair("Venue");
        String senderVenue = defaultVenueId(p.sender().accessToken(), p.senderTeam());
        String recipientVenue = defaultVenueId(p.recipient().accessToken(), p.recipientTeam());

        // The recipient is at home, so their venue is filled in.
        String requestId = id(send(p, daysFromNow(12), p.recipientTeam())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueId").value(recipientVenue))
                .andExpect(jsonPath("$.venue.id").value(recipientVenue))
                .andExpect(jsonPath("$.venue.name").value("Test Park"))
                .andExpect(jsonPath("$.venue.address").value("1 Test Road"))
                .andExpect(jsonPath("$.venue.latitude").value(56.014582))
                .andExpect(jsonPath("$.venue.longitude").value(-3.790261))
                .andExpect(jsonPath("$.senderTeam.id").value(p.senderTeam()))
                .andExpect(jsonPath("$.senderTeam.name").value("Venue FC Sender"))
                .andExpect(jsonPath("$.senderTeam.clubName").value("Venue FC Sender"))
                .andExpect(jsonPath("$.senderTeam.badgeUrl").value("https://example.com/badge.png"))
                .andExpect(jsonPath("$.recipientTeam.name").value("Venue FC Recipient"))
                .andExpect(jsonPath("$.proposedVenue").doesNotExist())
                .andExpect(jsonPath("$.senderContact").doesNotExist()));

        // Still negotiating: a second request between the same two teams is a duplicate.
        send(p, daysFromNow(19), p.senderTeam())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_REQUEST"));

        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/suggestChanges")
                        .header("Authorization", "Bearer " + p.recipient().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Can we play at yours?", "proposedVenueId": "%s" }
                                """.formatted(senderVenue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue.id").value(recipientVenue))
                .andExpect(jsonPath("$.proposedVenue.id").value(senderVenue))
                .andExpect(jsonPath("$.proposedVenue.name").value("Test Park"));

        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/acceptChanges")
                        .header("Authorization", "Bearer " + p.sender().accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue.id").value(senderVenue))
                .andExpect(jsonPath("$.proposedVenue").doesNotExist());

        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/accept")
                        .header("Authorization", "Bearer " + p.recipient().accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.senderContact.teamName").value("Venue FC Sender"))
                .andExpect(jsonPath("$.recipientContact.teamName").value("Venue FC Recipient"));

        mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", p.senderTeam())
                        .header("Authorization", "Bearer " + p.sender().accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].venueId").value(senderVenue))
                .andExpect(jsonPath("$[0].venue.id").value(senderVenue))
                .andExpect(jsonPath("$[0].venue.address").value("1 Test Road"))
                .andExpect(jsonPath("$[0].homeTeam.badgeUrl").value("https://example.com/badge.png"))
                .andExpect(jsonPath("$[0].awayTeam.badgeUrl").value("https://example.com/badge.png"));

        // Confirmed, so the two teams can arrange another game on a different date.
        send(p, daysFromNow(19), p.senderTeam())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.venueId").value(senderVenue));
    }

    @Test
    @DisplayName("fixtures with no venue of their own are played at the home team's default venue")
    void fixturesFallBackToTheHomeTeamsVenue() throws Exception {
        Pair p = readyPair("Fallback");
        String recipientVenue = defaultVenueId(p.recipient().accessToken(), p.recipientTeam());
        LocalDate date = LocalDate.now().plusDays(26);

        // A request saved before venues were filled in on send.
        Instant now = Instant.now();
        FriendlyRequest legacy = friendlyRequestRepository.save(new FriendlyRequest(null,
                p.senderTeam(), p.recipientTeam(), "no-slot-a", "no-slot-b", RequestStatus.SENT,
                date, LocalTime.of(10, 0), LocalTime.of(12, 0), null, p.recipientTeam(),
                CostShare.SPLIT, RefereeArrangement.NONE, null, p.sender().userId(), now, now,
                null, null, null, null));

        mockMvc.perform(post("/api/v1/friendly-requests/" + legacy.getId() + "/actions/accept")
                        .header("Authorization", "Bearer " + p.recipient().accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        String fixtures = mockMvc.perform(get("/api/v1/fixtures")
                        .param("teamId", p.recipientTeam())
                        .header("Authorization", "Bearer " + p.recipient().accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(fixtures).get(0);
        assertEquals(recipientVenue, created.get("venueId").asText(), "the fixture is created at the home venue");

        // A fixture stored with no venue at all is shown at the home venue at read time.
        // (Its own request: a request only ever has one fixture.)
        FriendlyRequest bareRequest = friendlyRequestRepository.save(new FriendlyRequest(null,
                p.senderTeam(), p.recipientTeam(), "no-slot-c", "no-slot-d", RequestStatus.CONFIRMED,
                date, LocalTime.of(14, 0), null, null, p.recipientTeam(),
                CostShare.SPLIT, RefereeArrangement.NONE, null, p.sender().userId(), now, now,
                null, null, null, null));
        Fixture bare = fixtureRepository.save(new Fixture(null, bareRequest.getId(), p.recipientTeam(), p.senderTeam(),
                date, LocalTime.of(14, 0), null, null, FixtureStatus.CONFIRMED, CostShare.SPLIT,
                RefereeArrangement.NONE, now));
        mockMvc.perform(get("/api/v1/fixtures/" + bare.getId())
                        .header("Authorization", "Bearer " + p.sender().accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueId").doesNotExist())
                .andExpect(jsonPath("$.venue.id").value(recipientVenue))
                .andExpect(jsonPath("$.venue.name").value("Test Park"));
    }

    @Test
    @DisplayName("a request can only name a venue of one of its two clubs, and venue postcodes are capped on update too")
    void venuesMustBelongToTheTwoClubs() throws Exception {
        Pair p = readyPair("Stranger");
        String senderVenue = defaultVenueId(p.sender().accessToken(), p.senderTeam());
        TestAccount outsider = registerAccount("Stranger Outsider Owner");
        String outsiderTeam = createTeam(outsider.accessToken(), "Stranger FC Outsider", 56.014582, -3.790261);
        makeTeamReadyForRequests(outsider.accessToken(), outsiderTeam);
        String outsiderVenue = defaultVenueId(outsider.accessToken(), outsiderTeam);

        // Another club's venue would otherwise show its details to both teams.
        send(p, daysFromNow(33), p.senderTeam(), outsiderVenue)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_VENUE"));
        send(p, daysFromNow(33), p.senderTeam(), "no-such-venue")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_VENUE"));

        String requestId = id(send(p, daysFromNow(33), p.senderTeam(), senderVenue)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue.id").value(senderVenue)));
        mockMvc.perform(post("/api/v1/friendly-requests/" + requestId + "/actions/suggestChanges")
                        .header("Authorization", "Bearer " + p.recipient().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Somewhere else?", "proposedVenueId": "%s" }
                                """.formatted(outsiderVenue)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_VENUE"));

        mockMvc.perform(patch("/api/v1/venues/" + senderVenue)
                        .header("Authorization", "Bearer " + p.sender().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "postcode": "FK2 7ZX FK2 7ZX" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(patch("/api/v1/venues/" + senderVenue)
                        .header("Authorization", "Bearer " + p.sender().accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "postcode": "fk2 7zx" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postcode").value("FK2 7ZX"));
    }

    // ---- Helpers --------------------------------------------------------

    private record Pair(TestAccount sender, TestAccount recipient, String senderTeam, String recipientTeam) {}

    private Pair readyPair(String prefix) throws Exception {
        TestAccount sender = registerAccount(prefix + " Sender Owner");
        TestAccount recipient = registerAccount(prefix + " Recipient Owner");
        verifyEmail(sender);
        String senderTeam = createTeam(sender.accessToken(), prefix + " FC Sender", 56.014582, -3.790261);
        String recipientTeam = createTeam(recipient.accessToken(), prefix + " FC Recipient", 56.014582, -3.790261);
        makeTeamReadyForRequests(sender.accessToken(), senderTeam);
        makeTeamReadyForRequests(recipient.accessToken(), recipientTeam);
        return new Pair(sender, recipient, senderTeam, recipientTeam);
    }

    /** The club venue makeTeamReadyForRequests added - its club's first, so its default. */
    private String defaultVenueId(String accessToken, String teamId) throws Exception {
        String clubId = objectMapper.readTree(mockMvc.perform(get("/api/v1/teams/" + teamId)
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getContentAsString()).get("clubId").asText();
        JsonNode venues = objectMapper.readTree(mockMvc.perform(get("/api/v1/venues")
                        .param("clubId", clubId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return venues.get(0).get("id").asText();
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

    /** Sends a request with no venueId, on fresh slots for {@code isoDate}. */
    private ResultActions send(Pair p, String isoDate, String homeTeamId) throws Exception {
        return send(p, isoDate, homeTeamId, null);
    }

    /** Sends a request naming {@code venueId} (left out when null), on fresh slots for {@code isoDate}. */
    private ResultActions send(Pair p, String isoDate, String homeTeamId, String venueId) throws Exception {
        String senderSlot = slot(p.sender().accessToken(), p.senderTeam(), isoDate);
        String recipientSlot = slot(p.recipient().accessToken(), p.recipientTeam(), isoDate);
        return mockMvc.perform(post("/api/v1/friendly-requests")
                .header("Authorization", "Bearer " + p.sender().accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "senderTeamId": "%s", "recipientTeamId": "%s",
                          "senderSlotId": "%s", "recipientSlotId": "%s",
                          "date": "%s", "startTime": "10:00:00", "endTime": "12:00:00",
                          "homeTeamId": "%s", "costShare": "SPLIT", "refereeArrangement": "NONE"%s
                        }
                        """.formatted(p.senderTeam(), p.recipientTeam(), senderSlot, recipientSlot, isoDate,
                        homeTeamId, venueId == null ? "" : ", \"venueId\": \"" + venueId + "\"")));
    }

    private String id(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }
}
