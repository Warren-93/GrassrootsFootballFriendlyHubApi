package com.gffh.api.repository;

import com.gffh.api.model.Team;
import com.gffh.api.repository.custom.TeamRepositoryCustom;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Team persistence. The candidate search for matching lives in
 * {@link TeamRepositoryCustom#findCandidates}, written against
 * {@code MongoTemplate} because it combines a geospatial stage with several
 * equality filters and a cap, which a derived query cannot express.
 */
public interface TeamRepository extends MongoRepository<Team, String>, TeamRepositoryCustom {

    List<Team> findByClubId(String clubId);

    List<Team> findByNameRegex(Pattern name, Limit limit);

    /** Case-insensitive name search, for ADM-05's team search. */
    default List<Team> searchByName(String query, int limit) {
        return findByNameRegex(Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE), Limit.of(limit));
    }
}
