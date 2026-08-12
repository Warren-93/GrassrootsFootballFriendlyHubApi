package com.gffh.api.repository;

import com.gffh.api.domain.MatchingWeightsVersion;

import java.util.List;
import java.util.Optional;

public interface MatchingWeightsVersionRepository {

    Optional<MatchingWeightsVersion> findActive();

    List<MatchingWeightsVersion> listAll();

    MatchingWeightsVersion save(MatchingWeightsVersion version);

    /** Clears the active flag on every version - called before saving a new active one. */
    void deactivateAll();
}
