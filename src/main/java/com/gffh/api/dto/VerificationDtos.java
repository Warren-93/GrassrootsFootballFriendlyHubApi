package com.gffh.api.dto;

import com.gffh.api.model.Team;
import com.gffh.api.model.VerificationRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

public final class VerificationDtos {

    private VerificationDtos() {}

    public record SubmitRequest(
            String affiliationNumber,
            @NotBlank String contactDetails,
            @NotEmpty List<String> evidenceUrls) {}

    public record RejectRequest(@NotBlank String reason) {}

    /** {@code teamName}/{@code clubName} let ADM-03's queue say who is asking without a lookup per row. */
    public record VerificationRequestView(
            String id, String teamId, String affiliationNumber, String contactDetails,
            List<String> evidenceUrls, String status, String firstRejectionReason,
            String finalRejectionReason, Instant submittedAt, Instant reviewedAt,
            String teamName, String clubName) {

        /** {@code team} may be null if it has since been deleted. */
        public static VerificationRequestView from(VerificationRequest r, Team team) {
            return new VerificationRequestView(r.id(), r.teamId(), r.affiliationNumber(), r.contactDetails(),
                    r.evidenceUrls(), r.status().name(), r.firstRejectionReason(), r.finalRejectionReason(),
                    r.submittedAt(), r.reviewedAt(),
                    team == null ? null : team.name(), team == null ? null : team.clubName());
        }
    }
}
