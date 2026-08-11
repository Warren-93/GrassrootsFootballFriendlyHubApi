package com.gffh.api.repository;

import com.gffh.api.domain.FriendlyRequest;

import java.util.List;
import java.util.Optional;

public interface FriendlyRequestRepository {

    Optional<FriendlyRequest> findById(String id);

    List<FriendlyRequest> findByTeamId(String teamId);

    /** Whether an open (non-terminal) negotiation already exists between these two teams. */
    boolean existsOpenBetween(String teamAId, String teamBId);

    FriendlyRequest save(FriendlyRequest request);
}
