package com.gffh.api.web;

import com.gffh.api.service.VerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Verification", description = "Team verification submission (SCR-PR-07) and admin review (ADM-03)")
public class VerificationController {

    private final VerificationService verificationService;

    public VerificationController(VerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @PostMapping("/api/v1/teams/{teamId}/verification")
    public ResponseEntity<VerificationDtos.VerificationRequestView> submit(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String teamId,
            @Valid @RequestBody VerificationDtos.SubmitRequest request) {
        return ResponseEntity.ok(verificationService.submit(principal.getSubject(), teamId, request));
    }

    @GetMapping("/api/v1/admin/verifications")
    public ResponseEntity<List<VerificationDtos.VerificationRequestView>> queue() {
        return ResponseEntity.ok(verificationService.queue());
    }

    @GetMapping("/api/v1/admin/verifications/{id}")
    public ResponseEntity<VerificationDtos.VerificationRequestView> get(@PathVariable String id) {
        return ResponseEntity.ok(verificationService.get(id));
    }

    @PostMapping("/api/v1/admin/verifications/{id}/approve")
    public ResponseEntity<VerificationDtos.VerificationRequestView> approve(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id) {
        return ResponseEntity.ok(verificationService.approve(principal.getSubject(), id));
    }

    @PostMapping("/api/v1/admin/verifications/{id}/reject")
    public ResponseEntity<VerificationDtos.VerificationRequestView> reject(
            @AuthenticationPrincipal Jwt principal, @PathVariable String id,
            @Valid @RequestBody VerificationDtos.RejectRequest request) {
        return ResponseEntity.ok(verificationService.reject(principal.getSubject(), id, request));
    }
}
