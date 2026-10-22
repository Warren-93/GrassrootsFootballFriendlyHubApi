package com.gffh.api.controller.admin;

import com.gffh.api.dto.FixtureDtos;
import com.gffh.api.dto.admin.AdminFixtureInspectorDtos;
import com.gffh.api.model.Fixture;
import com.gffh.api.model.enums.FixtureStatus;
import com.gffh.api.service.AuditLogService;
import com.gffh.api.service.FixtureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/fixtures")
@Tag(name = "Admin fixture inspector", description = "ADM-07: fixture list (search by team, date, status) and read-only fixture detail with the full audit trail")
public class AdminFixtureInspectorController {

    private final FixtureService fixtureService;
    private final AuditLogService auditLogService;

    public AdminFixtureInspectorController(FixtureService fixtureService, AuditLogService auditLogService) {
        this.fixtureService = fixtureService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<AdminFixtureInspectorDtos.FixtureListItem>> list(
            @RequestParam(required = false) String teamQuery,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) FixtureStatus status,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(fixtureService.searchForAdmin(teamQuery, from, to, status, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminFixtureInspectorDtos.FixtureInspectorView> get(@PathVariable String id) {
        Fixture fixture = fixtureService.getForAdmin(id);
        FixtureDtos.FixtureView view = FixtureDtos.FixtureView.from(fixture,
                fixtureService.team(fixture.homeTeamId()), fixtureService.team(fixture.awayTeamId()),
                fixtureService.venueFor(fixture), true);
        var auditTrail = auditLogService.list("FIXTURE", id, 200);
        return ResponseEntity.ok(new AdminFixtureInspectorDtos.FixtureInspectorView(view, auditTrail));
    }
}
