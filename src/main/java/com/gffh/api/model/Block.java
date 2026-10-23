package com.gffh.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * SCR-PR-11: one team blocking another. Blocking is symmetric for matching
 * purposes (see BlockRepository.blockedTeamIdsEitherDirection) but a block
 * still has a clear owner - only the blocking team can see it in a list or
 * undo it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Block {

    private String id;
    private String blockingTeamId;
    private String blockedTeamId;
    private String reason;
    private Instant createdAt;
}
