package com.gffh.api.web;

import com.gffh.api.domain.VerificationRequest;
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

    public record VerificationRequestView(
            String id, String teamId, String affiliationNumber, String contactDetails,
            List<String> evidenceUrls, String status, String firstRejectionReason,
            String finalRejectionReason, Instant submittedAt, Instant reviewedAt) {

        public static VerificationRequestView from(VerificationRequest r) {
            return new VerificationRequestView(r.id(), r.teamId(), r.affiliationNumber(), r.contactDetails(),
                    r.evidenceUrls(), r.status().name(), r.firstRejectionReason(), r.finalRejectionReason(),
                    r.submittedAt(), r.reviewedAt());
        }
    }
}
