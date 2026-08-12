package com.gffh.api.repository;

import com.gffh.api.domain.AgeGroup;
import com.gffh.api.domain.GeoPoint;
import com.gffh.api.domain.Gender;
import com.gffh.api.domain.Team;

import java.util.List;
import java.util.Optional;

/**
 * Team persistence.
 *
 * <p>Deliberately an interface over a hand-written Mongo implementation rather
 * than a derived-query Spring Data repository: {@link #findCandidates} combines
 * a geospatial stage with several equality filters and a cap, which a derived
 * method name cannot express and which needs to stay aligned with the compound
 * indexes in section 10 of the Technical Specification.
 */
public interface TeamRepository {

    Optional<Team> findById(String id);

    List<Team> findByClubId(String clubId);

    Team save(Team team);

    /**
     * Candidate opponents, narrowed in the database before scoring.
     *
     * <p>Gender is filtered here rather than in the engine because it is a hard
     * constraint with an index behind it; scoring 400 teams to discard 200 on a
     * boolean would waste the index.
     *
     * @param excludeClubId the searching team's club - a club does not need the
     *        platform to arrange a fixture between its own squads
     */
    List<Team> findCandidates(GeoPoint centre,
                              int radiusMiles,
                              AgeGroup ageGroup,
                              Gender gender,
                              List<String> formats,
                              List<String> abilityLevels,
                              boolean verifiedOnly,
                              String excludeClubId,
                              int limit);

    /** Case-insensitive name search, for ADM-05's team search. */
    List<Team> searchByName(String query, int limit);
}
