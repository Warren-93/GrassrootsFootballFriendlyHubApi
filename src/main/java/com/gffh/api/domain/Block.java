package com.gffh.api.domain;

import java.time.Instant;

/**
 * SCR-PR-11: one team blocking another. Blocking is symmetric for matching
 * purposes (see BlockRepository.blockedTeamIdsEitherDirection) but a block
 * still has a clear owner - only the blocking team can see it in a list or
 * undo it.
 */
public record Block(
        String id,
        String blockingTeamId,
        String blockedTeamId,
        String reason,
        Instant createdAt) {
}
