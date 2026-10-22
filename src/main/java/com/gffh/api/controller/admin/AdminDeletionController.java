package com.gffh.api.controller.admin;

import com.gffh.api.dto.admin.AdminDeletionDtos;
import com.gffh.api.model.enums.DeletionRequestStatus;
import com.gffh.api.model.enums.DeletionTargetType;
import com.gffh.api.service.AdminDeletionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/deletions")
@Tag(name = "Admin deletions", description = "Permanent deletes of users and teams: preview, request, and super admin approval")
public class AdminDeletionController {

    private final AdminDeletionService adminDeletionService;

    public AdminDeletionController(AdminDeletionService adminDeletionService) {
        this.adminDeletionService = adminDeletionService;
    }

    @GetMapping("/preview")
    public ResponseEntity<AdminDeletionDtos.DeletionPreview> preview(
            @RequestParam DeletionTargetType targetType, @RequestParam String targetId) {
        return ResponseEntity.ok(adminDeletionService.preview(targetType, targetId));
    }

    @GetMapping
    public ResponseEntity<List<AdminDeletionDtos.DeletionRequestView>> list(
            @RequestParam(required = false) DeletionRequestStatus status) {
        return ResponseEntity.ok(adminDeletionService.list(status));
    }

    @PostMapping
    public ResponseEntity<AdminDeletionDtos.DeletionRequestView> request(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AdminDeletionDtos.CreateDeletionRequest body) {
        return ResponseEntity.ok(adminDeletionService.request(principal.getSubject(), body));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<AdminDeletionDtos.DeletionRequestView> approve(
            @AuthenticationPrincipal Jwt principal, @PathVariable String requestId,
            @Valid @RequestBody AdminDeletionDtos.ApproveDeletionRequest body) {
        return ResponseEntity.ok(adminDeletionService.approve(principal.getSubject(), requestId, body));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<AdminDeletionDtos.DeletionRequestView> reject(
            @AuthenticationPrincipal Jwt principal, @PathVariable String requestId,
            @Valid @RequestBody(required = false) AdminDeletionDtos.RejectDeletionRequest body) {
        return ResponseEntity.ok(adminDeletionService.reject(principal.getSubject(), requestId, body));
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<AdminDeletionDtos.DeletionRequestView> cancel(
            @AuthenticationPrincipal Jwt principal, @PathVariable String requestId) {
        return ResponseEntity.ok(adminDeletionService.cancel(principal.getSubject(), requestId));
    }
}
