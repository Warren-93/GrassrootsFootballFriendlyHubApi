package com.gffh.api.repository;

import com.gffh.api.model.NotificationPreference;
import com.gffh.api.model.Report;
import com.gffh.api.model.Team;
import com.gffh.api.model.Venue;
import com.gffh.api.model.enums.AbilityLevel;
import com.gffh.api.support.AbstractIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents already in production were written by the old *Document mapping
 * classes: their {@code _class} names a class that no longer exists, some
 * predate fields added since, and some hold a division under its old name.
 * They must still load through the repositories.
 */
class LegacyStoredDocumentsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private VenueRepository venues;

    @Autowired
    private ReportRepository reports;

    @Autowired
    private NotificationPreferenceRepository preferences;

    @Test
    @DisplayName("a team written by the old TeamDocument mapping still loads, with defaults for missing fields")
    void oldTeamDocumentLoads() {
        String id = UUID.randomUUID().toString();
        Date created = Date.from(Instant.parse("2026-10-01T10:00:00Z"));
        mongoTemplate.getCollection("teams").insertOne(new Document("_id", id)
                .append("clubId", "club-legacy")
                .append("name", "Legacy FC")
                .append("ageGroup", "U12")
                .append("gender", "MIXED")
                .append("format", "SEVEN_A_SIDE")
                .append("abilityLevel", "INTERMEDIATE")
                .append("postcode", "FK8 1AA")
                .append("location", new Document("type", "Point").append("coordinates", List.of(-3.94, 56.12)))
                .append("travelRadiusMiles", 15)
                .append("homeAwayPreference", "EITHER")
                .append("verification", "VERIFIED")
                .append("createdAt", created)
                .append("updatedAt", created)
                .append("suspended", false)
                .append("archived", false)
                .append("_class", "com.gffh.api.repository.mongo.TeamDocument"));

        Team team = teamRepository.findById(id).orElseThrow();

        assertThat(team.getName()).isEqualTo("Legacy FC");
        assertThat(team.getAbilityLevel()).isEqualTo(AbilityLevel.DIVISION_2);
        assertThat(team.getLocation().longitude()).isEqualTo(-3.94);
        assertThat(team.getLocation().latitude()).isEqualTo(56.12);
        assertThat(team.getOrganisation()).isNull();
        assertThat(team.isSearchVisible()).isTrue();
        assertThat(team.isShareContactDetails()).isTrue();
        assertThat(team.getCreatedAt()).isEqualTo(created.toInstant());
    }

    @Test
    @DisplayName("old venues, reports and preferences load with their list and switch defaults")
    void oldDocumentsWithMissingFieldsLoad() {
        String venueId = UUID.randomUUID().toString();
        mongoTemplate.getCollection("venues").insertOne(new Document("_id", venueId)
                .append("clubId", "club-legacy")
                .append("name", "Old Park")
                .append("isDefault", true)
                .append("_class", "com.gffh.api.repository.mongo.VenueDocument"));
        Venue venue = venues.findById(venueId).orElseThrow();
        assertThat(venue.getFacilities()).isEmpty();
        assertThat(venue.isDefault()).isTrue();
        assertThat(venue.getLocation()).isNull();

        String reportId = UUID.randomUUID().toString();
        mongoTemplate.getCollection("reports").insertOne(new Document("_id", reportId)
                .append("reporterTeamId", "a")
                .append("reportedTeamId", "b")
                .append("type", "OTHER")
                .append("severity", "LOW")
                .append("status", "OPEN")
                .append("_class", "com.gffh.api.repository.mongo.ReportDocument"));
        Report report = reports.findById(reportId).orElseThrow();
        assertThat(report.getInternalNotes()).isEmpty();

        String userId = UUID.randomUUID().toString();
        mongoTemplate.getCollection("notificationPreferences").insertOne(new Document("_id", userId)
                .append("friendlyRequests", true)
                .append("fixtures", false)
                .append("verification", true)
                .append("messages", true)
                .append("_class", "com.gffh.api.repository.mongo.NotificationPreferenceDocument"));
        NotificationPreference preference = preferences.findByUserId(userId).orElseThrow();
        assertThat(preference.isFixtures()).isFalse();
        assertThat(preference.isEmailEnabled()).isTrue();
        assertThat(preference.isPushEnabled()).isTrue();
    }
}
