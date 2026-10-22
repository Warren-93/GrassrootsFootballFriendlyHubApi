package com.gffh.api.controller.admin;

import com.gffh.api.dto.admin.AdminAccountDtos;
import com.gffh.api.service.AdminAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/admins")
@Tag(name = "Admin accounts", description = "Who the signed-in admin is, and (super admin only) adding and removing admins")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping("/me")
    public ResponseEntity<AdminAccountDtos.MeView> me(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(adminAccountService.me(principal.getSubject()));
    }

    @GetMapping
    public ResponseEntity<List<AdminAccountDtos.AdminAccountView>> list(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(adminAccountService.list(principal.getSubject()));
    }

    @PostMapping
    public ResponseEntity<AdminAccountDtos.CreateAdminResponse> create(
            @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AdminAccountDtos.CreateAdminRequest request) {
        return ResponseEntity.ok(adminAccountService.create(principal.getSubject(), request));
    }

    @DeleteMapping("/{adminId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal, @PathVariable String adminId) {
        adminAccountService.delete(principal.getSubject(), adminId);
        return ResponseEntity.noContent().build();
    }
}
