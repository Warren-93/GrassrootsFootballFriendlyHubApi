package com.gffh.api.web;

import com.gffh.api.domain.Fixture;
import com.gffh.api.service.AuditLogService;
import com.gffh.api.service.FixtureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/fixtures")
@Tag(name = "Admin fixture inspector", description = "ADM-07: read-only fixture detail with the full audit trail")
public class AdminFixtureInspectorController {

    private final FixtureService fixtureService;
    private final AuditLogService auditLogService;

    public AdminFixtureInspectorController(FixtureService fixtureService, AuditLogService auditLogService) {
        this.fixtureService = fixtureService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminFixtureInspectorDtos.FixtureInspectorView> get(@PathVariable String id) {
        Fixture fixture = fixtureService.getForAdmin(id);
        FixtureDtos.FixtureView view = FixtureDtos.FixtureView.from(fixture,
                fixtureService.team(fixture.homeTeamId()), fixtureService.team(fixture.awayTeamId()));
        var auditTrail = auditLogService.list("FIXTURE", id, 200);
        return ResponseEntity.ok(new AdminFixtureInspectorDtos.FixtureInspectorView(view, auditTrail));
    }
}
