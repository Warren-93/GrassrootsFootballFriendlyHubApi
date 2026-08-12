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

    /**
     * Admin-only bulk override for team suspension ("Suspension immediately
     * removes the team from search and cancels open requests", ADM-05) -
     * bypasses {@code RequestStateMachine} deliberately, since this isn't a
     * normal user-driven transition.
     */
    void cancelOpenForTeam(String teamId);
}
