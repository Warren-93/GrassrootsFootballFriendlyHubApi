package com.gffh.api.service;

import com.gffh.api.dto.admin.AdminDeletionDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.DeletionRequest;
import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.model.enums.DeletionRequestStatus;
import com.gffh.api.model.enums.DeletionTargetType;
import com.gffh.api.repository.DeletionRequestRepository;
import com.gffh.api.repository.PlatformAdminRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Permanent deletes of users and teams, with two keys: any admin can ask for
 * one (with a reason, after seeing the preview), but it only runs when the
 * super admin approves it and re-enters their password - and their
 * authenticator code when those are on. A super admin deleting something
 * themselves goes through the same request-then-approve steps, so every
 * delete has the same record in the audit log.
 */
@Service
public class AdminDeletionService {

    private final DeletionRequestRepository deletionRequests;
    private final CascadeDeletionService cascadeDeletion;
    private final AdminAccountService adminAccounts;
    private final AdminAuthService adminAuth;
    private final PlatformAdminRepository admins;
    private final AuditLogService auditLogService;

    public AdminDeletionService(DeletionRequestRepository deletionRequests, CascadeDeletionService cascadeDeletion,
                                AdminAccountService adminAccounts, AdminAuthService adminAuth,
                                PlatformAdminRepository admins, AuditLogService auditLogService) {
        this.deletionRequests = deletionRequests;
        this.cascadeDeletion = cascadeDeletion;
        this.adminAccounts = adminAccounts;
        this.adminAuth = adminAuth;
        this.admins = admins;
        this.auditLogService = auditLogService;
    }

    public AdminDeletionDtos.DeletionPreview preview(DeletionTargetType type, String targetId) {
        return cascadeDeletion.preview(type, targetId);
    }

    public List<AdminDeletionDtos.DeletionRequestView> list(DeletionRequestStatus status) {
        return deletionRequests.list(status, 200).stream().map(AdminDeletionDtos.DeletionRequestView::from).toList();
    }

    /** One pending request per target: asking again returns the one already waiting. */
    public AdminDeletionDtos.DeletionRequestView request(String adminId, AdminDeletionDtos.CreateDeletionRequest body) {
        PlatformAdmin admin = admin(adminId);
        var existing = deletionRequests.findPendingFor(body.targetType(), body.targetId());
        if (existing.isPresent()) return AdminDeletionDtos.DeletionRequestView.from(existing.get());

        AdminDeletionDtos.DeletionPreview preview = cascadeDeletion.preview(body.targetType(), body.targetId());
        DeletionRequest saved = deletionRequests.save(new DeletionRequest(null, body.targetType(), body.targetId(),
                preview.targetLabel(), body.reason().trim(), DeletionRequestStatus.PENDING, admin.getId(), admin.getEmail(),
                Instant.now(), null, null, null, null, summarise(preview), null));
        auditLogService.record(adminId, "DELETION_REQUESTED", body.targetType().name(), body.targetId(),
                body.reason().trim());
        return AdminDeletionDtos.DeletionRequestView.from(saved);
    }

    /**
     * Claims the request (PENDING to COMPLETED) before deleting, so a second
     * approval at the same moment finds it already decided. If the delete
     * itself fails part-way the request goes back to PENDING: the delete is
     * safe to run again, picking up whatever was left.
     */
    public AdminDeletionDtos.DeletionRequestView approve(String adminId, String requestId,
                                                         AdminDeletionDtos.ApproveDeletionRequest body) {
        adminAccounts.requireSuperAdmin(adminId);
        PlatformAdmin approver = adminAuth.confirmIdentity(adminId, body.password(), body.code());
        DeletionRequest request = pending(requestId);

        if (cascadeDeletion.labelFor(request.getTargetType(), request.getTargetId()).isEmpty()) {
            deletionRequests.saveIfStatus(request.decided(DeletionRequestStatus.CANCELLED, approver,
                    "It had already been deleted.", null), DeletionRequestStatus.PENDING);
            throw new BusinessRuleException("DELETION_TARGET_GONE", HttpStatus.CONFLICT,
                    "That " + request.getTargetType().name().toLowerCase() + " has already been deleted.");
        }

        DeletionRequest claimed = deletionRequests.saveIfStatus(
                request.decided(DeletionRequestStatus.COMPLETED, approver, note(body.note()), null),
                DeletionRequestStatus.PENDING).orElseThrow(AdminDeletionService::alreadyDecided);

        Map<String, Long> removed;
        try {
            removed = cascadeDeletion.delete(request.getTargetType(), request.getTargetId());
        } catch (RuntimeException e) {
            deletionRequests.save(request);
            throw e;
        }
        String result = describe(removed);
        DeletionRequest done = deletionRequests.save(new DeletionRequest(claimed.getId(), claimed.getTargetType(),
                claimed.getTargetId(), claimed.getTargetLabel(), claimed.getReason(), claimed.getStatus(),
                claimed.getRequestedByAdminId(), claimed.getRequestedByEmail(), claimed.getRequestedAt(),
                claimed.getDecidedByAdminId(), claimed.getDecidedByEmail(), claimed.getDecidedAt(), claimed.getDecisionNote(),
                claimed.getSummary(), result));

        auditLogService.record(adminId, request.getTargetType().name() + "_DELETED", request.getTargetType().name(),
                request.getTargetId(), request.getTargetLabel() + " - requested by " + request.getRequestedByEmail()
                        + ": " + request.getReason() + ". Removed: " + result);
        return AdminDeletionDtos.DeletionRequestView.from(done);
    }

    public AdminDeletionDtos.DeletionRequestView reject(String adminId, String requestId,
                                                        AdminDeletionDtos.RejectDeletionRequest body) {
        adminAccounts.requireSuperAdmin(adminId);
        DeletionRequest request = pending(requestId);
        DeletionRequest rejected = deletionRequests.saveIfStatus(
                request.decided(DeletionRequestStatus.REJECTED, admin(adminId), note(body == null ? null : body.note()), null),
                DeletionRequestStatus.PENDING).orElseThrow(AdminDeletionService::alreadyDecided);
        auditLogService.record(adminId, "DELETION_REJECTED", request.getTargetType().name(), request.getTargetId(),
                rejected.getDecisionNote());
        return AdminDeletionDtos.DeletionRequestView.from(rejected);
    }

    /** The admin who asked, or the super admin, can withdraw a request that hasn't been decided. */
    public AdminDeletionDtos.DeletionRequestView cancel(String adminId, String requestId) {
        DeletionRequest request = pending(requestId);
        if (!request.getRequestedByAdminId().equals(adminId) && !adminAccounts.isSuperAdmin(adminId)) {
            throw new BusinessRuleException("SUPER_ADMIN_ONLY", HttpStatus.FORBIDDEN,
                    "Only the admin who asked, or the super admin, can withdraw this request.");
        }
        DeletionRequest cancelled = deletionRequests.saveIfStatus(
                request.decided(DeletionRequestStatus.CANCELLED, admin(adminId), "Withdrawn.", null),
                DeletionRequestStatus.PENDING).orElseThrow(AdminDeletionService::alreadyDecided);
        auditLogService.record(adminId, "DELETION_WITHDRAWN", request.getTargetType().name(), request.getTargetId(), null);
        return AdminDeletionDtos.DeletionRequestView.from(cancelled);
    }

    private DeletionRequest pending(String requestId) {
        DeletionRequest request = deletionRequests.findById(requestId).orElseThrow(() -> new BusinessRuleException(
                "DELETION_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "That delete request could not be found."));
        if (request.getStatus() != DeletionRequestStatus.PENDING) throw alreadyDecided();
        return request;
    }

    private PlatformAdmin admin(String adminId) {
        return admins.findById(adminId).orElseThrow(() -> new BusinessRuleException(
                "ADMIN_NOT_FOUND", HttpStatus.UNAUTHORIZED, "Your sign-in session has expired. Please sign in again."));
    }

    private static BusinessRuleException alreadyDecided() {
        return new BusinessRuleException("DELETION_ALREADY_DECIDED", HttpStatus.CONFLICT,
                "This delete request has already been dealt with. Refresh to see where it stands.");
    }

    private static String note(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private static String summarise(AdminDeletionDtos.DeletionPreview p) {
        StringBuilder s = new StringBuilder(describe(p.records().stream().collect(Collectors.toMap(
                AdminDeletionDtos.CountLine::label, AdminDeletionDtos.CountLine::count, (a, b) -> a,
                java.util.LinkedHashMap::new))));
        if (!p.clubsDeleted().isEmpty()) {
            s.append(". Clubs: ").append(p.clubsDeleted().stream().map(AdminDeletionDtos.NamedItem::name)
                    .collect(Collectors.joining(", ")));
        }
        if (!p.teamsDeleted().isEmpty()) {
            s.append(". Teams: ").append(p.teamsDeleted().stream().map(AdminDeletionDtos.NamedItem::name)
                    .collect(Collectors.joining(", ")));
        }
        return s.toString();
    }

    private static String describe(Map<String, Long> counts) {
        if (counts.isEmpty()) return "nothing";
        return counts.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
    }
}
