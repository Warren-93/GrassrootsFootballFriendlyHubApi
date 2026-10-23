package com.gffh.api.repository.mongo;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Document("fixtures")
public class FixtureDocument {

    @Id
    public String id;
    public String friendlyRequestId;
    public String homeTeamId;
    public String awayTeamId;
    public LocalDate date;
    public LocalTime startTime;
    public LocalTime endTime;
    public String venueId;
    public String status;
    public String costShare;
    public String refereeArrangement;
    public Instant createdAt;

    public static FixtureDocument from(Fixture f) {
        FixtureDocument d = new FixtureDocument();
        d.id = f.getId();
        d.friendlyRequestId = f.getFriendlyRequestId();
        d.homeTeamId = f.getHomeTeamId();
        d.awayTeamId = f.getAwayTeamId();
        d.date = f.getDate();
        d.startTime = f.getStartTime();
        d.endTime = f.getEndTime();
        d.venueId = f.getVenueId();
        d.status = f.getStatus().name();
        d.costShare = f.getCostShare().name();
        d.refereeArrangement = f.getRefereeArrangement().name();
        d.createdAt = f.getCreatedAt();
        return d;
    }

    public Fixture toDomain() {
        return new Fixture(id, friendlyRequestId, homeTeamId, awayTeamId, date, startTime, endTime,
                venueId, FixtureStatus.valueOf(status), CostShare.valueOf(costShare),
                RefereeArrangement.valueOf(refereeArrangement), createdAt);
    }
}
