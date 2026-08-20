package com.gffh.api.web;

import com.gffh.api.domain.Block;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class BlockDtos {

    private BlockDtos() {}

    public record BlockRequest(@NotBlank String blockedTeamId, String reason) {}

    public record BlockView(String id, String blockedTeamId, String blockedTeamName, String reason, Instant createdAt) {
        public static BlockView from(Block block, String blockedTeamName) {
            return new BlockView(block.id(), block.blockedTeamId(), blockedTeamName, block.reason(), block.createdAt());
        }
    }
}
