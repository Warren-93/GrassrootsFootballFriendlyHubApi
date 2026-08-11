package com.gffh.api.repository.mongo;

import com.gffh.api.domain.*;
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
        d.id = f.id();
        d.friendlyRequestId = f.friendlyRequestId();
        d.homeTeamId = f.homeTeamId();
        d.awayTeamId = f.awayTeamId();
        d.date = f.date();
        d.startTime = f.startTime();
        d.endTime = f.endTime();
        d.venueId = f.venueId();
        d.status = f.status().name();
        d.costShare = f.costShare().name();
        d.refereeArrangement = f.refereeArrangement().name();
        d.createdAt = f.createdAt();
        return d;
    }

    public Fixture toDomain() {
        return new Fixture(id, friendlyRequestId, homeTeamId, awayTeamId, date, startTime, endTime,
                venueId, FixtureStatus.valueOf(status), CostShare.valueOf(costShare),
                RefereeArrangement.valueOf(refereeArrangement), createdAt);
    }
}
