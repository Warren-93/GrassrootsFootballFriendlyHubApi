package com.gffh.api.repository.mongo;

import com.gffh.api.model.GeoPoint;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.AgeGroup;
import com.gffh.api.model.enums.Gender;
import com.gffh.api.model.enums.VerificationStatus;
import com.gffh.api.repository.TeamRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
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
    private static final double METRES_PER_MILE = 1609.344;

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
        Team toSave = team.getId() != null ? team : withGeneratedId(team, now);
        toSave = toSave.getCreatedAt() != null ? toSave : withCreatedAt(toSave, now);
        TeamDocument doc = TeamDocument.from(toSave);
        doc.updatedAt = now;
        mongoTemplate.save(doc, COLLECTION);
        return doc.toDomain();
    }

    private Team withGeneratedId(Team team, Instant now) {
        return new Team(UUID.randomUUID().toString(), team.getClubId(), team.getName(), team.getClubName(),
                team.getBadgeUrl(), team.getAgeGroup(), team.getGender(), team.getFormat(), team.getAbilityLevel(), team.getOrganisation(),
                team.getLeague(), team.getPostcode(), team.getLocation(), team.getTravelRadiusMiles(),
                team.getHomeAwayPreference(), team.getManagerName(), team.getContactPhone(), team.getDescription(),
                team.getVerification(), team.getDefaultVenueId(), team.getFirstFixtureConfirmedAt(),
                now, now, team.isSuspended(), team.isArchived(), team.isSearchVisible(), team.isShareContactDetails());
    }

    private Team withCreatedAt(Team team, Instant now) {
        return new Team(team.getId(), team.getClubId(), team.getName(), team.getClubName(),
                team.getBadgeUrl(), team.getAgeGroup(), team.getGender(), team.getFormat(), team.getAbilityLevel(), team.getOrganisation(),
                team.getLeague(), team.getPostcode(), team.getLocation(), team.getTravelRadiusMiles(),
                team.getHomeAwayPreference(), team.getManagerName(), team.getContactPhone(), team.getDescription(),
                team.getVerification(), team.getDefaultVenueId(), team.getFirstFixtureConfirmedAt(),
                now, now, team.isSuspended(), team.isArchived(), team.isSearchVisible(), team.isShareContactDetails());
    }

    @Override
    public List<Team> findCandidates(GeoPoint centre, int radiusMiles, Collection<AgeGroup> ageGroups,
                                     Gender gender, List<String> formats, List<String> abilityLevels,
                                     boolean verifiedOnly, String excludeClubId, int limit) {
        // $nearSphere rather than $geoWithin so the candidate cap keeps the
        // closest teams: matching no longer has a travel-radius wall, and the
        // cap must not drop nearby teams in favour of distant ones. GeoJSON
        // distances are in metres.
        Criteria criteria = Criteria.where("location")
                .nearSphere(new GeoJsonPoint(centre.longitude(), centre.latitude()))
                .maxDistance(radiusMiles * METRES_PER_MILE)
                .and("ageGroup").in(ageGroups.stream().map(Enum::name).toList())
                .and("clubId").ne(excludeClubId)
                .and("suspended").ne(true)
                .and("archived").ne(true)
                .and("searchVisible").ne(false);

        // Gender must match exactly - see Gender.isCompatibleWith.
        criteria = criteria.and("gender").is(gender.name());
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
