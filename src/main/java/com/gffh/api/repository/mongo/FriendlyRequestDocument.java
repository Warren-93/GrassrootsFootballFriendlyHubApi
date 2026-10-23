package com.gffh.api.repository.mongo;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Document("friendlyRequests")
public class FriendlyRequestDocument {

    @Id
    public String id;
    public String senderTeamId;
    public String recipientTeamId;
    public String senderSlotId;
    public String recipientSlotId;
    public String status;
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String venueId;
    public String homeTeamId;
    public String costShare;
    public String refereeArrangement;
    public String message;
    public String createdByUserId;
    public Instant createdAt;
    public Instant updatedAt;
    public String actionReason;
    public LocalTime proposedStartTime;
    public LocalTime proposedEndTime;
    public String proposedVenueId;

    public static FriendlyRequestDocument from(FriendlyRequest r) {
        FriendlyRequestDocument d = new FriendlyRequestDocument();
        d.id = r.getId();
        d.senderTeamId = r.getSenderTeamId();
        d.recipientTeamId = r.getRecipientTeamId();
        d.senderSlotId = r.getSenderSlotId();
        d.recipientSlotId = r.getRecipientSlotId();
        d.status = r.getStatus().name();
        d.date = r.getDate();
        d.startTime = r.getStartTime();
        d.endTime = r.getEndTime();
        d.venueId = r.getVenueId();
        d.homeTeamId = r.getHomeTeamId();
        d.costShare = r.getCostShare().name();
        d.refereeArrangement = r.getRefereeArrangement().name();
        d.message = r.getMessage();
        d.createdByUserId = r.getCreatedByUserId();
        d.createdAt = r.getCreatedAt();
        d.updatedAt = r.getUpdatedAt();
        d.actionReason = r.getActionReason();
        d.proposedStartTime = r.getProposedStartTime();
        d.proposedEndTime = r.getProposedEndTime();
        d.proposedVenueId = r.getProposedVenueId();
        return d;
    }

    public FriendlyRequest toDomain() {
        return new FriendlyRequest(id, senderTeamId, recipientTeamId, senderSlotId, recipientSlotId,
                RequestStatus.valueOf(status), date, startTime, endTime, venueId, homeTeamId,
                CostShare.valueOf(costShare), RefereeArrangement.valueOf(refereeArrangement),
                message, createdByUserId, createdAt, updatedAt, actionReason,
                proposedStartTime, proposedEndTime, proposedVenueId);
    }
}
