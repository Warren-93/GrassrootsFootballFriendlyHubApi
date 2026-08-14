package com.gffh.api.web;

import com.gffh.api.domain.AuditLogEntry;

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
}
