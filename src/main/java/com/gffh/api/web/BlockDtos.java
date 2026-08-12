package com.gffh.api.web;

import jakarta.validation.constraints.NotBlank;

public final class BlockDtos {

    private BlockDtos() {}

    public record BlockRequest(@NotBlank String blockedTeamId, String reason) {}
}
