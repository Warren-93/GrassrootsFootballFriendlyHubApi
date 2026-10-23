package com.gffh.api.repository;

import com.gffh.api.model.MatchingWeightsVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface MatchingWeightsVersionRepository extends MongoRepository<MatchingWeightsVersion, String> {

    Optional<MatchingWeightsVersion> findFirstByActiveTrue();

    List<MatchingWeightsVersion> findAllByOrderByPublishedAtDesc();

    /** Clears the active flag on every version - called before saving a new active one. */
    @Query("{}")
    @Update("{ $set: { 'active': false } }")
    void deactivateAll();
}
