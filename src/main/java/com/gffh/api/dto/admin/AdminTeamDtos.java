package com.gffh.api.dto.admin;

import com.gffh.api.model.Team;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class AdminTeamDtos {

    private AdminTeamDtos() {}

    public record MergeRequest(@NotBlank String primaryTeamId, @NotBlank String duplicateTeamId) {}

    public record AdminTeamView(
            String id, String clubId, String name, String clubName, String ageGroup, String gender,
            String format, String abilityLevel, String organisation, String verification, boolean suspended,
            boolean archived, String league, String postcode, Instant createdAt, Instant updatedAt) {

        public static AdminTeamView from(Team t) {
            return new AdminTeamView(t.getId(), t.getClubId(), t.getName(), t.getClubName(), t.getAgeGroup().name(),
                    t.getGender().name(), t.getFormat().name(), t.getAbilityLevel().name(),
                    t.getOrganisation() == null ? null : t.getOrganisation().name(), t.getVerification().name(),
                    t.isSuspended(), t.isArchived(), t.getLeague(), t.getPostcode(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
