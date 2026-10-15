package com.gffh.api.service;

import com.gffh.api.domain.*;
import com.gffh.api.repository.ReportRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.web.ReportDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** SCR-PR-11 submission and ADM-04's moderation queue. */
@Service
public class ReportService {

    private final ReportRepository reports;
    private final MembershipService membershipService;
    private final AuditLogService auditLogService;
    private final TeamRepository teams;

    public ReportService(ReportRepository reports, MembershipService membershipService,
                         AuditLogService auditLogService, TeamRepository teams) {
        this.reports = reports;
        this.membershipService = membershipService;
        this.auditLogService = auditLogService;
        this.teams = teams;
    }

    public ReportDtos.ReportView submit(String userId, String reporterTeamId, ReportDtos.SubmitRequest request) {
        membershipService.requireCanManageTeam(userId, reporterTeamId);
        ReportType type = parse(ReportType.class, request.type(), "type");
        ReportSeverity severity = parse(ReportSeverity.class, request.severity(), "severity");

        Report saved = reports.save(new Report(null, reporterTeamId, request.reportedTeamId(),
                request.relatedFixtureId(), type, severity, blankToNull(request.details()), ReportStatus.OPEN, null, List.of(), Instant.now(), null));
        return view(saved);
    }

    public List<ReportDtos.ReportView> queue() {
        return reports.findByStatus(ReportStatus.OPEN).stream().map(this::view).toList();
    }

    public ReportDtos.ReportView get(String id) {
        return view(find(id));
    }

    public ReportDtos.ReportView assign(String adminId, String id) {
        Report current = find(id);
        Report updated = new Report(current.id(), current.reporterTeamId(), current.reportedTeamId(),
                current.relatedFixtureId(), current.type(), current.severity(), current.details(), current.status(),
                adminId, current.internalNotes(), current.createdAt(), null);
        reports.save(updated);
        auditLogService.record(adminId, "REPORT_ASSIGNED", "REPORT", id, null);
        return get(id);
    }

    public ReportDtos.ReportView addNote(String adminId, String id, ReportDtos.NoteRequest request) {
        Report current = find(id);
        List<InternalNote> notes = new ArrayList<>(current.internalNotes());
        notes.add(new InternalNote(adminId, request.note(), Instant.now()));
        Report updated = new Report(current.id(), current.reporterTeamId(), current.reportedTeamId(),
                current.relatedFixtureId(), current.type(), current.severity(), current.details(), current.status(),
                current.assignedAdminId(), notes, current.createdAt(), null);
        reports.save(updated);
        return get(id);
    }

    /** {@code action} is one of dismiss, warn, suspend, escalate - every action requires a note (ADM-04's rule). */
    public ReportDtos.ReportView act(String adminId, String id, String action, ReportDtos.ActionRequest request) {
        Report current = find(id);
        ReportStatus newStatus = switch (action) {
            case "dismiss" -> ReportStatus.DISMISSED;
            case "warn" -> ReportStatus.WARNED;
            case "suspend" -> ReportStatus.SUSPENDED;
            case "escalate" -> ReportStatus.ESCALATED;
            default -> throw new BusinessRuleException("UNKNOWN_REPORT_ACTION", HttpStatus.BAD_REQUEST,
                    "Unknown action: " + action);
        };

        List<InternalNote> notes = new ArrayList<>(current.internalNotes());
        notes.add(new InternalNote(adminId, "[" + action + "] " + request.reason(), Instant.now()));

        Report updated = new Report(current.id(), current.reporterTeamId(), current.reportedTeamId(),
                current.relatedFixtureId(), current.type(), current.severity(), current.details(), newStatus,
                current.assignedAdminId(), notes, current.createdAt(), null);
        reports.save(updated);
        auditLogService.record(adminId, "REPORT_" + action.toUpperCase(), "REPORT", id, request.reason());
        return get(id);
    }

    private ReportDtos.ReportView view(Report r) {
        return ReportDtos.ReportView.from(r, teamName(r.reporterTeamId()), teamName(r.reportedTeamId()));
    }

    private String teamName(String teamId) {
        return teamId == null ? null : teams.findById(teamId).map(Team::name).orElse(null);
    }

    private Report find(String id) {
        return reports.findById(id).orElseThrow(() -> new BusinessRuleException(
                "REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "That report could not be found."));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String fieldName) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("INVALID_" + fieldName.toUpperCase(), HttpStatus.BAD_REQUEST,
                    "Invalid " + fieldName + ": " + value);
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
