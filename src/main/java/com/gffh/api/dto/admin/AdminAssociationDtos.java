package com.gffh.api.dto.admin;

import com.gffh.api.model.RegisteredOrganisation;
import com.gffh.api.model.enums.Organisation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AdminAssociationDtos {

    private AdminAssociationDtos() {}

    /** Add or edit an association's details. Only {@code name} is required. */
    public record OrganisationInput(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 20) String shortName,
            Organisation linkedOrganisation,
            @Size(max = 120) String contactName,
            @Email @Size(max = 200) String contactEmail,
            @Size(max = 200) String website,
            @Size(max = 1000) String notes) {}

    /** Add or edit a league; {@code divisions} in display order, blanks and repeats dropped. */
    public record LeagueInput(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 50) List<@Size(max = 80) String> divisions) {}

    public record LeagueView(String id, String name, List<String> divisions) {}

    public record OrganisationView(
            String id, String name, String shortName, String linkedOrganisation, String linkedOrganisationLabel,
            String contactName, String contactEmail, String website, String notes, List<LeagueView> leagues,
            Instant createdAt, String createdByEmail, Instant updatedAt) {

        public static OrganisationView from(RegisteredOrganisation o) {
            return new OrganisationView(o.id(), o.name(), o.shortName(),
                    o.linkedOrganisation() == null ? null : o.linkedOrganisation().name(),
                    o.linkedOrganisation() == null ? null : o.linkedOrganisation().label(),
                    o.contactName(), o.contactEmail(), o.website(), o.notes(),
                    o.leagues().stream().map(l -> new LeagueView(l.id(), l.name(), l.divisions())).toList(),
                    o.createdAt(), o.createdByEmail(), o.updatedAt());
        }
    }
}
