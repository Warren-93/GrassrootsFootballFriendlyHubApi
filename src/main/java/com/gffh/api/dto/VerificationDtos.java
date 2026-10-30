package com.gffh.api.dto;

import com.gffh.api.model.Team;
import com.gffh.api.model.VerificationRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class VerificationDtos {

    private VerificationDtos() {}

    public record SubmitRequest(
            @Size(max = 100) String affiliationNumber,
            @NotBlank @Size(max = 500) String contactDetails,
            @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 500) String> evidenceUrls) {}

    public record RejectRequest(@NotBlank String reason) {}

    /** {@code teamName}/{@code clubName} let ADM-03's queue say who is asking without a lookup per row. */
    public record VerificationRequestView(
            String id, String teamId, String affiliationNumber, String contactDetails,
            List<String> evidenceUrls, String status, String firstRejectionReason,
            String finalRejectionReason, Instant submittedAt, Instant reviewedAt,
            String teamName, String clubName) {

        /** {@code team} may be null if it has since been deleted. */
        public static VerificationRequestView from(VerificationRequest r, Team team) {
            return new VerificationRequestView(r.getId(), r.getTeamId(), r.getAffiliationNumber(), r.getContactDetails(),
                    r.getEvidenceUrls(), r.getStatus().name(), r.getFirstRejectionReason(), r.getFinalRejectionReason(),
                    r.getSubmittedAt(), r.getReviewedAt(),
                    team == null ? null : team.getName(), team == null ? null : team.getClubName());
        }
    }
}
