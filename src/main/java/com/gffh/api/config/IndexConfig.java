package com.gffh.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import org.bson.Document;

/**
 * Explicit index creation - deliberate rather than {@code auto-index-creation},
 * per section 10 of the Technical Specification and the note in
 * {@code application.yml}. Indexes are created idempotently on startup; in a
 * production topology this would instead run as a migration ahead of a
 * deploy, but a startup step keeps a fresh environment usable immediately.
 */
@Component
public class IndexConfig {

    private final MongoTemplate mongoTemplate;

    public IndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void ensureIndexes() {
        var teams = mongoTemplate.indexOps("teams");
        teams.ensureIndex(new GeospatialIndex("location").typed(org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE));
        teams.ensureIndex(new CompoundIndexDefinition(
                new Document("ageGroup", 1).append("format", 1).append("gender", 1).append("clubId", 1)));
        teams.ensureIndex(new Index("clubId", org.springframework.data.domain.Sort.Direction.ASC));

        var availability = mongoTemplate.indexOps("availability");
        availability.ensureIndex(new CompoundIndexDefinition(
                new Document("teamId", 1).append("date", 1).append("status", 1)));

        var blocks = mongoTemplate.indexOps("blocks");
        blocks.ensureIndex(new CompoundIndexDefinition(
                new Document("blockingTeamId", 1).append("blockedTeamId", 1)));
        blocks.ensureIndex(new Index("blockedTeamId", org.springframework.data.domain.Sort.Direction.ASC));

        var users = mongoTemplate.indexOps("users");
        users.ensureIndex(new Index("email", org.springframework.data.domain.Sort.Direction.ASC).unique());

        var memberships = mongoTemplate.indexOps("memberships");
        memberships.ensureIndex(new CompoundIndexDefinition(new Document("userId", 1).append("teamId", 1)));
        memberships.ensureIndex(new CompoundIndexDefinition(new Document("userId", 1).append("clubId", 1)));

        var friendlyRequests = mongoTemplate.indexOps("friendlyRequests");
        friendlyRequests.ensureIndex(new CompoundIndexDefinition(
                new Document("senderTeamId", 1).append("status", 1)));
        friendlyRequests.ensureIndex(new CompoundIndexDefinition(
                new Document("recipientTeamId", 1).append("status", 1)));

        var fixtures = mongoTemplate.indexOps("fixtures");
        fixtures.ensureIndex(new Index("homeTeamId", org.springframework.data.domain.Sort.Direction.ASC));
        fixtures.ensureIndex(new Index("awayTeamId", org.springframework.data.domain.Sort.Direction.ASC));
        fixtures.ensureIndex(new Index("date", org.springframework.data.domain.Sort.Direction.ASC));

        var refreshTokens = mongoTemplate.indexOps("refreshTokens");
        refreshTokens.ensureIndex(new Index("tokenHash", org.springframework.data.domain.Sort.Direction.ASC).unique());
        refreshTokens.ensureIndex(new Index("userId", org.springframework.data.domain.Sort.Direction.ASC));

        var verificationTokens = mongoTemplate.indexOps("verificationTokens");
        verificationTokens.ensureIndex(new CompoundIndexDefinition(
                new Document("token", 1).append("purpose", 1)).unique());
        verificationTokens.ensureIndex(new Index("userId", org.springframework.data.domain.Sort.Direction.ASC));

        var venues = mongoTemplate.indexOps("venues");
        venues.ensureIndex(new Index("clubId", org.springframework.data.domain.Sort.Direction.ASC));

        // A retry long after this window is a new attempt, not a resend.
        var idempotencyKeys = mongoTemplate.indexOps("idempotencyKeys");
        idempotencyKeys.ensureIndex(new Index("createdAt", org.springframework.data.domain.Sort.Direction.ASC)
                .expire(java.time.Duration.ofHours(24)));
    }
}
