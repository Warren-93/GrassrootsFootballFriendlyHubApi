package com.gffh.api.repository.custom;

import com.gffh.api.model.GeoPoint;
import com.gffh.api.model.Team;
import com.gffh.api.model.enums.AgeGroup;
import com.gffh.api.model.enums.Gender;
import com.gffh.api.model.enums.VerificationStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collection;
import java.util.List;

/**
 * Goes through {@link MongoTemplate} so the compound and geospatial indexes in
 * {@link com.gffh.api.config.IndexConfig} are actually exercised.
 */
public class TeamRepositoryCustomImpl implements TeamRepositoryCustom {

    private static final double METRES_PER_MILE = 1609.344;

    private final MongoTemplate mongoTemplate;

    public TeamRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
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
        return mongoTemplate.find(query, Team.class);
    }
}
