package com.gffh.api.web;

import com.gffh.api.service.PrivacyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Privacy", description = "SCR-PR-10: export or delete a user's own data")
public class PrivacyController {

    private final PrivacyService privacyService;

    public PrivacyController(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @GetMapping("/api/v1/me/export")
    public ResponseEntity<PrivacyDtos.AccountExport> export(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(privacyService.export(principal.getSubject()));
    }

    @DeleteMapping("/api/v1/me")
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Jwt principal) {
        privacyService.deleteAccount(principal.getSubject());
        return ResponseEntity.noContent().build();
    }
}
