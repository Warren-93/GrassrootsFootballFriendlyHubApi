package com.gffh.api.dto.admin;

import com.gffh.api.model.DeletionRequest;
import com.gffh.api.model.enums.DeletionTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AdminDeletionDtos {

    private AdminDeletionDtos() {}

    public record NamedItem(String id, String name) {}

    public record CountLine(String label, long count) {}

    /**
     * Everything a permanent delete would remove, worked out from the data as
     * it is now. {@code otherTeamsAffected} are teams outside the delete with
     * an open request or confirmed fixture against it: those are cancelled and
     * the team told. {@code warnings} are things left behind that an admin
     * may want to sort out, such as a club left without a club admin.
     */
    public record DeletionPreview(
            String targetType,
            String targetId,
            String targetLabel,
            List<NamedItem> teamsDeleted,
            List<NamedItem> clubsDeleted,
            List<String> otherTeamsAffected,
            List<CountLine> records,
            List<String> warnings) {}

    public record CreateDeletionRequest(
            @NotNull DeletionTargetType targetType,
            @NotBlank String targetId,
            @NotBlank @Size(max = 500) String reason) {}

    /** {@code code} is only needed when authenticator codes are on. */
    public record ApproveDeletionRequest(@NotBlank String password, String code, @Size(max = 500) String note) {}

    public record RejectDeletionRequest(@Size(max = 500) String note) {}

    public record DeletionRequestView(
            String id,
            String targetType,
            String targetId,
            String targetLabel,
            String reason,
            String status,
            String requestedByAdminId,
            String requestedByEmail,
            Instant requestedAt,
            String decidedByAdminId,
            String decidedByEmail,
            Instant decidedAt,
            String decisionNote,
            String summary,
            String result) {

        public static DeletionRequestView from(DeletionRequest r) {
            return new DeletionRequestView(r.id(), r.targetType().name(), r.targetId(), r.targetLabel(), r.reason(),
                    r.status().name(), r.requestedByAdminId(), r.requestedByEmail(), r.requestedAt(),
                    r.decidedByAdminId(), r.decidedByEmail(), r.decidedAt(), r.decisionNote(), r.summary(), r.result());
        }
    }
}
