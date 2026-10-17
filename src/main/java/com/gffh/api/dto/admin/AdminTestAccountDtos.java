package com.gffh.api.dto.admin;

import com.gffh.api.model.*;
import com.gffh.api.model.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;

/** Request and response shapes for /api/v1/admin/test-accounts. */
public final class AdminTestAccountDtos {

    private AdminTestAccountDtos() {}

    /**
     * {@code password} is the one every account in the batch signs in with.
     * {@code emailDomain} defaults to example.com, which never receives mail.
     * {@code team} only applies to {@link TestAccountKind#MANAGER}.
     */
    public record CreateRequest(
            @NotNull TestAccountKind kind,
            @Min(1) @Max(10) int count,
            @NotBlank @Size(min = 10, max = 72) String password,
            @Pattern(regexp = "^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "must be a domain like example.com") String emailDomain,
            @Valid TeamOptions team) {}

    /** Every field is optional; the defaults make a mixed U12 team in Denny. */
    public record TeamOptions(
            AgeGroup ageGroup,
            Gender gender,
            Format format,
            AbilityLevel abilityLevel,
            String postcode,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude) {}

    public record TestAccountView(
            String id, String userId, String email, String displayName, String kind,
            boolean emailVerified, String clubId, String teamId, String teamName, Instant createdAt) {}
}
