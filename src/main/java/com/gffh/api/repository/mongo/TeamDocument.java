package com.gffh.api.repository.mongo;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
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
    public String organisation;
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
    /** Nullable so pre-existing documents (written before this field existed) default to visible/shared, not hidden. */
    public Boolean searchVisible;
    public Boolean shareContactDetails;

    public static TeamDocument from(Team t) {
        TeamDocument d = new TeamDocument();
        d.id = t.getId();
        d.clubId = t.getClubId();
        d.name = t.getName();
        d.clubName = t.getClubName();
        d.badgeUrl = t.getBadgeUrl();
        d.ageGroup = t.getAgeGroup().name();
        d.gender = t.getGender().name();
        d.format = t.getFormat().name();
        d.abilityLevel = t.getAbilityLevel().name();
        d.organisation = t.getOrganisation() == null ? null : t.getOrganisation().name();
        d.league = t.getLeague();
        d.postcode = t.getPostcode();
        d.location = new GeoJsonPoint(t.getLocation().longitude(), t.getLocation().latitude());
        d.travelRadiusMiles = t.getTravelRadiusMiles();
        d.homeAwayPreference = t.getHomeAwayPreference().name();
        d.managerName = t.getManagerName();
        d.contactPhone = t.getContactPhone();
        d.description = t.getDescription();
        d.verification = t.getVerification().name();
        d.defaultVenueId = t.getDefaultVenueId();
        d.firstFixtureConfirmedAt = t.getFirstFixtureConfirmedAt();
        d.createdAt = t.getCreatedAt();
        d.updatedAt = t.getUpdatedAt();
        d.suspended = t.isSuspended();
        d.archived = t.isArchived();
        d.searchVisible = t.isSearchVisible();
        d.shareContactDetails = t.isShareContactDetails();
        return d;
    }

    public Team toDomain() {
        return new Team(
                id, clubId, name, clubName, badgeUrl,
                AgeGroup.valueOf(ageGroup),
                Gender.valueOf(gender),
                Format.valueOf(format),
                AbilityLevel.fromValue(abilityLevel),
                organisation == null ? null : Organisation.valueOf(organisation),
                league, postcode,
                new GeoPoint(location.getX(), location.getY()),
                travelRadiusMiles,
                HomeAwayPreference.valueOf(homeAwayPreference),
                managerName, contactPhone, description,
                VerificationStatus.valueOf(verification),
                defaultVenueId, firstFixtureConfirmedAt, createdAt, updatedAt, suspended, archived,
                searchVisible == null || searchVisible,
                shareContactDetails == null || shareContactDetails);
    }
}
