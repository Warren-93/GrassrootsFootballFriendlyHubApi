package com.gffh.api.model;

import com.gffh.api.model.enums.CostShare;
import com.gffh.api.model.enums.FixtureStatus;
import com.gffh.api.model.enums.RefereeArrangement;
import com.gffh.api.model.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Fixture {

    private String id;
    private String friendlyRequestId;
    private String homeTeamId;
    private String awayTeamId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venueId;
    private FixtureStatus status;
    private CostShare costShare;
    private RefereeArrangement refereeArrangement;
    private Instant createdAt;

    public boolean involves(String teamId) {
        return homeTeamId.equals(teamId) || awayTeamId.equals(teamId);
    }
}
