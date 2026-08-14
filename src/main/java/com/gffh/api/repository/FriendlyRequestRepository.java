package com.gffh.api.repository;

import com.gffh.api.domain.FriendlyRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FriendlyRequestRepository {

    Optional<FriendlyRequest> findById(String id);

    List<FriendlyRequest> findByTeamId(String teamId);

    /** CONFIRMED requests whose date has already passed - the fixture auto-completion job's input. */
    List<FriendlyRequest> findConfirmedBefore(LocalDate date);

    /** Whether an open (non-terminal) negotiation already exists between these two teams. */
    boolean existsOpenBetween(String teamAId, String teamBId);

    /** Whether any request, of any status, has ever existed between these two teams - past or present relationship. */
    boolean existsBetween(String teamAId, String teamBId);

    /** Whether this team (sender or recipient) has any open (non-terminal) negotiation - blocks archiving it out from under it. */
    boolean existsOpenForTeam(String teamId);

    FriendlyRequest save(FriendlyRequest request);

    /**
     * Admin-only bulk override for team suspension ("Suspension immediately
     * removes the team from search and cancels open requests", ADM-05) -
     * bypasses {@code RequestStateMachine} deliberately, since this isn't a
     * normal user-driven transition.
     */
    void cancelOpenForTeam(String teamId);
}
