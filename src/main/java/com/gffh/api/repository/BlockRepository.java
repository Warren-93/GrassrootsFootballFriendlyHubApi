package com.gffh.api.repository;

import java.util.Set;

public interface BlockRepository {

    /**
     * Teams that must not appear in this team's results, in either direction.
     * Blocking is symmetric: a team that blocks you also disappears from your
     * results, so that blocking cannot be detected by its absence on one side.
     */
    Set<String> blockedTeamIdsEitherDirection(String teamId);

    void block(String blockingTeamId, String blockedTeamId, String reason);
}
