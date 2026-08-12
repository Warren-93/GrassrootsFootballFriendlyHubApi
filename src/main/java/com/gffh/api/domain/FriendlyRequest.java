package com.gffh.api.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A proposed fixture under negotiation. {@link RequestStatus} and the legal
 * transitions between its values are owned entirely by
 * {@link com.gffh.api.request.RequestStateMachine}; this record only carries the
 * data the negotiation is about.
 *
 * <p>{@code senderSlotId} and {@code recipientSlotId} reserve the availability
 * windows the proposal is built on, so the same window cannot be re-proposed to
 * a second team while this negotiation is open.
 */
public record FriendlyRequest(
        String id,
        String senderTeamId,
        String recipientTeamId,
        String senderSlotId,
        String recipientSlotId,
        RequestStatus status,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String venueId,
        String homeTeamId,
        CostShare costShare,
        RefereeArrangement refereeArrangement,
        String message,
        String createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        /** The reason given for the most recent status change (e.g. why changes were requested, or declined). */
        String actionReason) {

    public boolean involves(String teamId) {
        return senderTeamId.equals(teamId) || recipientTeamId.equals(teamId);
    }

    public String awayTeamId() {
        return homeTeamId.equals(senderTeamId) ? recipientTeamId : senderTeamId;
    }
}
