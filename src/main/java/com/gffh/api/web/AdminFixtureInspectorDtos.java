package com.gffh.api.web;

import com.gffh.api.domain.AuditLogEntry;

import java.util.List;

public final class AdminFixtureInspectorDtos {

    private AdminFixtureInspectorDtos() {}

    /**
     * Message content is deliberately absent: no messaging feature exists yet
     * (see the mobile README's backend-gaps list) - "conversation viewer" per
     * ADM-07 stays blocked until it does.
     */
    public record FixtureInspectorView(FixtureDtos.FixtureView fixture, List<AuditLogEntry> auditTrail) {}
}
