package com.gffh.api.web;

import com.gffh.api.domain.Team;
import jakarta.validation.constraints.NotBlank;

public final class AdminTeamDtos {

    private AdminTeamDtos() {}

    public record MergeRequest(@NotBlank String primaryTeamId, @NotBlank String duplicateTeamId) {}

    public record AdminTeamView(
            String id, String clubId, String name, String clubName, String ageGroup, String gender,
            String format, String abilityLevel, String verification, boolean suspended) {

        public static AdminTeamView from(Team t) {
            return new AdminTeamView(t.id(), t.clubId(), t.name(), t.clubName(), t.ageGroup().name(),
                    t.gender().name(), t.format().name(), t.abilityLevel().name(), t.verification().name(),
                    t.suspended());
        }
    }
}
