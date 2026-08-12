package com.gffh.api.repository.mongo;

import com.gffh.api.domain.*;
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

    public static FriendlyRequestDocument from(FriendlyRequest r) {
        FriendlyRequestDocument d = new FriendlyRequestDocument();
        d.id = r.id();
        d.senderTeamId = r.senderTeamId();
        d.recipientTeamId = r.recipientTeamId();
        d.senderSlotId = r.senderSlotId();
        d.recipientSlotId = r.recipientSlotId();
        d.status = r.status().name();
        d.date = r.date();
        d.startTime = r.startTime();
        d.endTime = r.endTime();
        d.venueId = r.venueId();
        d.homeTeamId = r.homeTeamId();
        d.costShare = r.costShare().name();
        d.refereeArrangement = r.refereeArrangement().name();
        d.message = r.message();
        d.createdByUserId = r.createdByUserId();
        d.createdAt = r.createdAt();
        d.updatedAt = r.updatedAt();
        d.actionReason = r.actionReason();
        return d;
    }

    public FriendlyRequest toDomain() {
        return new FriendlyRequest(id, senderTeamId, recipientTeamId, senderSlotId, recipientSlotId,
                RequestStatus.valueOf(status), date, startTime, endTime, venueId, homeTeamId,
                CostShare.valueOf(costShare), RefereeArrangement.valueOf(refereeArrangement),
                message, createdByUserId, createdAt, updatedAt, actionReason);
    }
}
