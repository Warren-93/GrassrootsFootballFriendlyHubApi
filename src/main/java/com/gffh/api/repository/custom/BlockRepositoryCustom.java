package com.gffh.api.repository.custom;

import java.util.Set;

/** Block operations that a derived query method can't express. */
public interface BlockRepositoryCustom {

    /**
     * Teams that must not appear in this team's results, in either direction.
     * Blocking is symmetric: a team that blocks you also disappears from your
     * results, so that blocking cannot be detected by its absence on one side.
     */
    Set<String> blockedTeamIdsEitherDirection(String teamId);

    /** Idempotent: a pair that's already blocked keeps its original block. */
    void block(String blockingTeamId, String blockedTeamId, String reason);
}
