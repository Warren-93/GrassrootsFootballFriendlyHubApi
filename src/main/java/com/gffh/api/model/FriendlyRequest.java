package com.gffh.api.model;

import com.gffh.api.model.enums.CostShare;
import com.gffh.api.model.enums.RefereeArrangement;
import com.gffh.api.model.enums.RequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A proposed fixture under negotiation. {@link RequestStatus} and the legal
 * transitions between its values are owned entirely by
 * {@link com.gffh.api.service.RequestStateMachine}; this record only carries the
 * data the negotiation is about.
 *
 * <p>{@code senderSlotId} and {@code recipientSlotId} reserve the availability
 * windows the proposal is built on, so the same window cannot be re-proposed to
 * a second team while this negotiation is open.
 */
@Document("friendlyRequests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FriendlyRequest {

    @Id
    private String id;
    private String senderTeamId;
    private String recipientTeamId;
    private String senderSlotId;
    private String recipientSlotId;
    private RequestStatus status;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venueId;
    private String homeTeamId;
    private CostShare costShare;
    private RefereeArrangement refereeArrangement;
    private String message;
    private String createdByUserId;
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    /** The reason given for the most recent status change (e.g. why changes were requested, or declined). */
    private String actionReason;
    /**
         * A pending counter-proposal from {@code suggestChanges} - date is
         * deliberately not proposable here, since {@code senderSlotId}/
         * {@code recipientSlotId} are reserved against the original date and
         * re-matching them to a different one is out of scope. Null until a
         * proposal exists; applied to the real fields and cleared the moment
         * {@code acceptChanges} is called.
         */
    private LocalTime proposedStartTime;
    private LocalTime proposedEndTime;
    private String proposedVenueId;

    public boolean involves(String teamId) {
        return senderTeamId.equals(teamId) || recipientTeamId.equals(teamId);
    }

    public String awayTeamId() {
        return homeTeamId.equals(senderTeamId) ? recipientTeamId : senderTeamId;
    }
}
