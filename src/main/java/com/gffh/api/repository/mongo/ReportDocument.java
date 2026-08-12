package com.gffh.api.repository.mongo;

import com.gffh.api.domain.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("reports")
public class ReportDocument {

    public record InternalNoteDoc(String adminId, String note, Instant createdAt) {}

    @Id
    public String id;
    public String reporterTeamId;
    public String reportedTeamId;
    public String relatedFixtureId;
    public String type;
    public String severity;
    public String status;
    public String assignedAdminId;
    public List<InternalNoteDoc> internalNotes;
    public Instant createdAt;
    public Instant updatedAt;

    public static ReportDocument from(Report r) {
        ReportDocument d = new ReportDocument();
        d.id = r.id();
        d.reporterTeamId = r.reporterTeamId();
        d.reportedTeamId = r.reportedTeamId();
        d.relatedFixtureId = r.relatedFixtureId();
        d.type = r.type().name();
        d.severity = r.severity().name();
        d.status = r.status().name();
        d.assignedAdminId = r.assignedAdminId();
        d.internalNotes = r.internalNotes().stream()
                .map(n -> new InternalNoteDoc(n.adminId(), n.note(), n.createdAt())).toList();
        d.createdAt = r.createdAt();
        d.updatedAt = r.updatedAt();
        return d;
    }

    public Report toDomain() {
        List<InternalNote> notes = internalNotes == null ? List.of() : internalNotes.stream()
                .map(n -> new InternalNote(n.adminId(), n.note(), n.createdAt())).toList();
        return new Report(id, reporterTeamId, reportedTeamId, relatedFixtureId,
                ReportType.valueOf(type), ReportSeverity.valueOf(severity), ReportStatus.valueOf(status),
                assignedAdminId, notes, createdAt, updatedAt);
    }
}
