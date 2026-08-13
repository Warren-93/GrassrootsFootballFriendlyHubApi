package com.gffh.api.repository.mongo;

import com.gffh.api.domain.AgeGroup;
import com.gffh.api.domain.GeoPoint;
import com.gffh.api.domain.Gender;
import com.gffh.api.domain.Team;
import com.gffh.api.domain.VerificationStatus;
import com.gffh.api.repository.TeamRepository;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hand-written rather than a derived Spring Data repository - see the javadoc
 * on {@link TeamRepository#findCandidates}. All queries go through
 * {@link MongoTemplate} so the compound and geospatial indexes in
 * {@link com.gffh.api.config.IndexConfig} are actually exercised.
 */
@Repository
public class MongoTeamRepository implements TeamRepository {

    private static final String COLLECTION = "teams";

    private final MongoTemplate mongoTemplate;

    public MongoTeamRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<Team> findById(String id) {
        TeamDocument doc = mongoTemplate.findById(id, TeamDocument.class, COLLECTION);
        return Optional.ofNullable(doc).map(TeamDocument::toDomain);
    }

    @Override
    public List<Team> findByClubId(String clubId) {
        Query query = new Query(Criteria.where("clubId").is(clubId));
        return mongoTemplate.find(query, TeamDocument.class, COLLECTION).stream()
                .map(TeamDocument::toDomain).toList();
    }

    @Override
    public Team save(Team team) {
        Instant now = Instant.now();
        Team toSave = team.id() != null ? team : withGeneratedId(team, now);
        toSave = toSave.createdAt() != null ? toSave : withCreatedAt(toSave, now);
        TeamDocument doc = TeamDocument.from(toSave);
        doc.updatedAt = now;
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private Team withGeneratedId(Team team, Instant now) {
        return new Team(UUID.randomUUID().toString(), team.clubId(), team.name(), team.clubName(),
                team.badgeUrl(), team.ageGroup(), team.gender(), team.format(), team.abilityLevel(),
                team.league(), team.postcode(), team.location(), team.travelRadiusMiles(),
                team.homeAwayPreference(), team.managerName(), team.contactPhone(), team.description(),
                team.verification(), team.defaultVenueId(), team.firstFixtureConfirmedAt(),
                now, now, team.suspended(), team.archived());
    }

    private Team withCreatedAt(Team team, Instant now) {
        return new Team(team.id(), team.clubId(), team.name(), team.clubName(),
                team.badgeUrl(), team.ageGroup(), team.gender(), team.format(), team.abilityLevel(),
                team.league(), team.postcode(), team.location(), team.travelRadiusMiles(),
                team.homeAwayPreference(), team.managerName(), team.contactPhone(), team.description(),
                team.verification(), team.defaultVenueId(), team.firstFixtureConfirmedAt(),
                now, now, team.suspended(), team.archived());
    }

    @Override
    public List<Team> findCandidates(GeoPoint centre, int radiusMiles, AgeGroup ageGroup, Gender gender,
                                     List<String> formats, List<String> abilityLevels,
                                     boolean verifiedOnly, String excludeClubId, int limit) {
        Circle circle = new Circle(new Point(centre.longitude(), centre.latitude()),
                new Distance(radiusMiles, Metrics.MILES));

        Criteria criteria = Criteria.where("location").withinSphere(circle)
                .and("ageGroup").is(ageGroup.name())
                .and("clubId").ne(excludeClubId)
                .and("suspended").ne(true)
                .and("archived").ne(true);

        // MIXED is compatible with everything, so only a non-mixed search team
        // narrows the candidate set by gender.
        if (gender != Gender.MIXED) {
            criteria = criteria.and("gender").in(gender.name(), Gender.MIXED.name());
        }
        if (formats != null && !formats.isEmpty()) {
            criteria = criteria.and("format").in(formats);
        }
        if (abilityLevels != null && !abilityLevels.isEmpty()) {
            criteria = criteria.and("abilityLevel").in(abilityLevels);
        }
        if (verifiedOnly) {
            criteria = criteria.and("verification").is(VerificationStatus.VERIFIED.name());
        }

        Query query = new Query(criteria).limit(limit);
        return mongoTemplate.find(query, TeamDocument.class, COLLECTION).stream()
                .map(TeamDocument::toDomain).toList();
    }

    @Override
    public List<Team> searchByName(String query, int limit) {
        Query q = new Query(Criteria.where("name").regex(java.util.regex.Pattern.quote(query), "i")).limit(limit);
        return mongoTemplate.find(q, TeamDocument.class, COLLECTION).stream()
                .map(TeamDocument::toDomain).toList();
    }
}
