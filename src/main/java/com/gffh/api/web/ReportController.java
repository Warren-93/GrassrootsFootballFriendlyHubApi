package com.gffh.api.web;

import com.gffh.api.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Reports", description = "Report submission (SCR-PR-11) and admin moderation (ADM-04)")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/api/v1/teams/{teamId}/reports")
    public ResponseEntity<ReportDtos.ReportView> submit(
            @AuthenticationPrincipal Jwt principal, @PathVariable String teamId,
            @Valid @RequestBody ReportDtos.SubmitRequest request) {
        return ResponseEntity.ok(reportService.submit(principal.getSubject(), teamId, request));
    }

    @GetMapping("/api/v1/admin/reports")
    public ResponseEntity<List<ReportDtos.ReportView>> queue() {
        return ResponseEntity.ok(reportService.queue());
    }

    @GetMapping("/api/v1/admin/reports/{id}")
    public ResponseEntity<ReportDtos.ReportView> get(@PathVariable String id) {
        return ResponseEntity.ok(reportService.get(id));
    }

    @PostMapping("/api/v1/admin/reports/{id}/assign")
    public ResponseEntity<ReportDtos.ReportView> assign(@AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        return ResponseEntity.ok(reportService.assign(principal.getSubject(), id));
    }

    @PostMapping("/api/v1/admin/reports/{id}/notes")
    public ResponseEntity<ReportDtos.ReportView> addNote(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id,
            @Valid @RequestBody ReportDtos.NoteRequest request) {
        return ResponseEntity.ok(reportService.addNote(principal.getSubject(), id, request));
    }

    @PostMapping("/api/v1/admin/reports/{id}/actions/{action}")
    public ResponseEntity<ReportDtos.ReportView> act(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id, @PathVariable String action,
            @Valid @RequestBody ReportDtos.ActionRequest request) {
        return ResponseEntity.ok(reportService.act(principal.getSubject(), id, action, request));
    }
}
