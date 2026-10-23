package com.gffh.api.model;

import com.gffh.api.model.enums.ReportSeverity;
import com.gffh.api.model.enums.ReportStatus;
import com.gffh.api.model.enums.ReportType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * SCR-PR-11's report submission and ADM-04's moderation queue. Message
 * content ("conversation viewer" per ADM-04) is out of scope until messaging
 * exists at all - see the mobile README's backend-gaps list.
 */
@Document("reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    private String id;
    private String reporterTeamId;
    private String reportedTeamId;
    private String relatedFixtureId;
    private ReportType type;
    private ReportSeverity severity;
    private String details;
    private ReportStatus status;
    private String assignedAdminId;
    private List<InternalNote> internalNotes = List.of();
    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
}
