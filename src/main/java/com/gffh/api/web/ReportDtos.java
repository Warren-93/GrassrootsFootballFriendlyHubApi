package com.gffh.api.web;

import com.gffh.api.domain.InternalNote;
import com.gffh.api.domain.Report;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {}

    public record SubmitRequest(
            @NotBlank String reportedTeamId,
            String relatedFixtureId,
            @NotNull String type,
            @NotNull String severity,
            @Size(max = 1000) String details) {}

    public record ActionRequest(@NotBlank String reason) {}

    public record NoteRequest(@NotBlank String note) {}

    public record InternalNoteView(String adminId, String note, Instant createdAt) {
        public static InternalNoteView from(InternalNote n) {
            return new InternalNoteView(n.adminId(), n.note(), n.createdAt());
        }
    }

    /** The team names are for ADM-04's queue; either is null if that team no longer exists. */
    public record ReportView(
            String id, String reporterTeamId, String reportedTeamId, String relatedFixtureId,
            String type, String severity, String details, String status, String assignedAdminId,
            List<InternalNoteView> internalNotes, Instant createdAt, Instant updatedAt,
            String reporterTeamName, String reportedTeamName) {

        public static ReportView from(Report r, String reporterTeamName, String reportedTeamName) {
            return new ReportView(r.id(), r.reporterTeamId(), r.reportedTeamId(), r.relatedFixtureId(),
                    r.type().name(), r.severity().name(), r.details(), r.status().name(), r.assignedAdminId(),
                    r.internalNotes().stream().map(InternalNoteView::from).toList(),
                    r.createdAt(), r.updatedAt(), reporterTeamName, reportedTeamName);
        }
    }
}
