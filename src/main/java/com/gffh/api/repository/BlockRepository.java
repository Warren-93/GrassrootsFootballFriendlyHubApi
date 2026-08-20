package com.gffh.api.repository;

import com.gffh.api.domain.Block;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BlockRepository {

    /**
     * Teams that must not appear in this team's results, in either direction.
     * Blocking is symmetric: a team that blocks you also disappears from your
     * results, so that blocking cannot be detected by its absence on one side.
     */
    Set<String> blockedTeamIdsEitherDirection(String teamId);

    void block(String blockingTeamId, String blockedTeamId, String reason);

    /** The blocks *this* team placed - one-directional, unlike the matching-time set above. */
    List<Block> findByBlockingTeamId(String teamId);

    Optional<Block> findById(String id);

    void deleteById(String id);
}
