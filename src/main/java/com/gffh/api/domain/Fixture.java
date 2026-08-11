package com.gffh.api.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A confirmed friendly. Created the moment a {@link FriendlyRequest} reaches
 * {@link RequestStatus#ACCEPTED}, per the state machine's own note that
 * ACCEPTED is transient - the service creates this and confirms in the same
 * step. Kept as a separate record, rather than just relabelling the request, so
 * that fixture history survives even if requests are later archived.
 */
public record Fixture(
        String id,
        String friendlyRequestId,
        String homeTeamId,
        String awayTeamId,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String venueId,
        FixtureStatus status,
        CostShare costShare,
        RefereeArrangement refereeArrangement,
        Instant createdAt) {

    public boolean involves(String teamId) {
        return homeTeamId.equals(teamId) || awayTeamId.equals(teamId);
    }
}
