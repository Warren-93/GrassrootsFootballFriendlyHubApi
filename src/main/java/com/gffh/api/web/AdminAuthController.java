package com.gffh.api.web;

import com.gffh.api.service.AdminAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Admin auth", description = "Platform administrator sign-in with mandatory TOTP second factor (ADM-01)")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<AdminAuthDtos.BootstrapResponse> bootstrap(
            @Valid @RequestBody AdminAuthDtos.BootstrapRequest request) {
        return ResponseEntity.ok(adminAuthService.bootstrap(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AdminAuthDtos.LoginStep1Response> login(
            @Valid @RequestBody AdminAuthDtos.LoginRequest request) {
        return ResponseEntity.ok(adminAuthService.login(request));
    }

    @PostMapping("/login/verify")
    public ResponseEntity<AdminAuthDtos.SessionResponse> verify(
            @Valid @RequestBody AdminAuthDtos.VerifyRequest request) {
        return ResponseEntity.ok(adminAuthService.verify(request));
    }
}
