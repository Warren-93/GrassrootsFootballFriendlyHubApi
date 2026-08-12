package com.gffh.api.domain;

import java.time.Instant;
import java.util.List;

/**
 * SCR-PR-11's report submission and ADM-04's moderation queue. Message
 * content ("conversation viewer" per ADM-04) is out of scope until messaging
 * exists at all - see the mobile README's backend-gaps list.
 */
public record Report(
        String id,
        String reporterTeamId,
        String reportedTeamId,
        String relatedFixtureId,
        ReportType type,
        ReportSeverity severity,
        ReportStatus status,
        String assignedAdminId,
        List<InternalNote> internalNotes,
        Instant createdAt,
        Instant updatedAt) {
}
