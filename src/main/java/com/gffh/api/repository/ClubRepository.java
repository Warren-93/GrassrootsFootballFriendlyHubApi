package com.gffh.api.repository;

import com.gffh.api.model.Club;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.regex.Pattern;

public interface ClubRepository extends MongoRepository<Club, String> {

    List<Club> findByNameRegex(Pattern name, Limit limit);

    /** Case-insensitive name search, for ADM-05's club search. */
    default List<Club> searchByName(String query, int limit) {
        return findByNameRegex(Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE), Limit.of(limit));
    }
}
