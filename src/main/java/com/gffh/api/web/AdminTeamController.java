package com.gffh.api.web;

import com.gffh.api.service.AdminTeamService;
import com.gffh.api.service.VerificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/teams")
@Tag(name = "Admin teams", description = "ADM-05 clubs and teams: search, suspend, merge duplicates, direct verify")
public class AdminTeamController {

    private final AdminTeamService adminTeamService;
    private final VerificationService verificationService;

    public AdminTeamController(AdminTeamService adminTeamService, VerificationService verificationService) {
        this.adminTeamService = adminTeamService;
        this.verificationService = verificationService;
    }

    @GetMapping
    public ResponseEntity<List<AdminTeamDtos.AdminTeamView>> search(@RequestParam String query) {
        return ResponseEntity.ok(adminTeamService.search(query));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<AdminTeamDtos.AdminTeamView> get(@PathVariable String teamId) {
        return ResponseEntity.ok(adminTeamService.get(teamId));
    }

    @PostMapping("/{teamId}/suspend")
    public ResponseEntity<AdminTeamDtos.AdminTeamView> suspend(
            @AuthenticationPrincipal Jwt principal, @PathVariable String teamId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(adminTeamService.suspend(principal.getSubject(), teamId, reason));
    }

    @PostMapping("/{teamId}/unsuspend")
    public ResponseEntity<AdminTeamDtos.AdminTeamView> unsuspend(
            @AuthenticationPrincipal Jwt principal, @PathVariable String teamId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(adminTeamService.unsuspend(principal.getSubject(), teamId, reason));
    }

    @PostMapping("/merge")
    public ResponseEntity<Void> merge(@AuthenticationPrincipal Jwt principal,
                                      @Valid @RequestBody AdminTeamDtos.MergeRequest request) {
        adminTeamService.merge(principal.getSubject(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{teamId}/verify")
    public ResponseEntity<AdminTeamDtos.AdminTeamView> verify(
            @AuthenticationPrincipal Jwt principal, @PathVariable String teamId) {
        verificationService.verifyDirectly(principal.getSubject(), teamId);
        return ResponseEntity.ok(adminTeamService.get(teamId));
    }
}
