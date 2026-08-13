package com.gffh.api.repository.mongo;

import com.gffh.api.domain.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Mongo mapping for {@link Team}. Kept separate from the domain record so the
 * domain package stays free of persistence annotations - see the README's
 * layout note on {@code domain}.
 */
@Document("teams")
public class TeamDocument {

    @Id
    public String id;
    public String clubId;
    public String name;
    public String clubName;
    public String badgeUrl;
    public String ageGroup;
    public String gender;
    public String format;
    public String abilityLevel;
    public String league;
    public String postcode;
    /** GeoJSON, longitude first - see {@link GeoPoint}. */
    public GeoJsonPoint location;
    public int travelRadiusMiles;
    public String homeAwayPreference;
    public String managerName;
    public String contactPhone;
    public String description;
    public String verification;
    public String defaultVenueId;
    public Instant firstFixtureConfirmedAt;
    public Instant createdAt;
    public Instant updatedAt;
    public boolean suspended;
    public boolean archived;

    public static TeamDocument from(Team t) {
        TeamDocument d = new TeamDocument();
        d.id = t.id();
        d.clubId = t.clubId();
        d.name = t.name();
        d.clubName = t.clubName();
        d.badgeUrl = t.badgeUrl();
        d.ageGroup = t.ageGroup().name();
        d.gender = t.gender().name();
        d.format = t.format().name();
        d.abilityLevel = t.abilityLevel().name();
        d.league = t.league();
        d.postcode = t.postcode();
        d.location = new GeoJsonPoint(t.location().longitude(), t.location().latitude());
        d.travelRadiusMiles = t.travelRadiusMiles();
        d.homeAwayPreference = t.homeAwayPreference().name();
        d.managerName = t.managerName();
        d.contactPhone = t.contactPhone();
        d.description = t.description();
        d.verification = t.verification().name();
        d.defaultVenueId = t.defaultVenueId();
        d.firstFixtureConfirmedAt = t.firstFixtureConfirmedAt();
        d.createdAt = t.createdAt();
        d.updatedAt = t.updatedAt();
        d.suspended = t.suspended();
        d.archived = t.archived();
        return d;
    }

    public Team toDomain() {
        return new Team(
                id, clubId, name, clubName, badgeUrl,
                AgeGroup.valueOf(ageGroup),
                Gender.valueOf(gender),
                Format.valueOf(format),
                AbilityLevel.valueOf(abilityLevel),
                league, postcode,
                new GeoPoint(location.getX(), location.getY()),
                travelRadiusMiles,
                HomeAwayPreference.valueOf(homeAwayPreference),
                managerName, contactPhone, description,
                VerificationStatus.valueOf(verification),
                defaultVenueId, firstFixtureConfirmedAt, createdAt, updatedAt, suspended, archived);
    }
}
