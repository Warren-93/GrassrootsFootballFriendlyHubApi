package com.gffh.api.dto.admin;

import com.gffh.api.controller.admin.AdminConversationController;
import com.gffh.api.dto.FixtureDtos;
import com.gffh.api.model.AuditLogEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class AdminFixtureInspectorDtos {

    private AdminFixtureInspectorDtos() {}

    /**
     * Message content is a separate lookup, not embedded here: conversations
     * are team-pair-scoped, not fixture-scoped, so the client fetches the
     * transcript via GET /api/v1/admin/conversations/between using this
     * fixture's home/away team IDs - see AdminConversationController.
     */
    public record FixtureInspectorView(FixtureDtos.FixtureView fixture, List<AuditLogEntry> auditTrail) {}

    /** One row of the admin fixture list; open it with GET /api/v1/admin/fixtures/{id}. */
    public record FixtureListItem(String id, LocalDate date, LocalTime startTime, LocalTime endTime, String status,
                                  String homeTeamId, String homeTeamName, String awayTeamId, String awayTeamName,
                                  String venueName, Instant createdAt) {}
}
