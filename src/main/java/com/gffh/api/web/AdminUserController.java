package com.gffh.api.web;

import com.gffh.api.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin users", description = "ADM-06 Users: search, role inspection, suspend, force password reset, resend verification")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<List<AdminUserDtos.AdminUserView>> search(@RequestParam String query) {
        return ResponseEntity.ok(adminUserService.search(query));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserDtos.AdminUserView> get(@PathVariable String userId) {
        return ResponseEntity.ok(adminUserService.get(userId));
    }

    @PostMapping("/{userId}/suspend")
    public ResponseEntity<AdminUserDtos.AdminUserView> suspend(
            @AuthenticationPrincipal Jwt principal, @PathVariable String userId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(adminUserService.suspend(principal.getSubject(), userId, reason));
    }

    @PostMapping("/{userId}/unsuspend")
    public ResponseEntity<AdminUserDtos.AdminUserView> unsuspend(
            @AuthenticationPrincipal Jwt principal, @PathVariable String userId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(adminUserService.unsuspend(principal.getSubject(), userId, reason));
    }

    @PostMapping("/{userId}/force-password-reset")
    public ResponseEntity<Void> forcePasswordReset(@AuthenticationPrincipal Jwt principal, @PathVariable String userId) {
        adminUserService.forcePasswordReset(principal.getSubject(), userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/resend-verification")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal Jwt principal, @PathVariable String userId) {
        adminUserService.resendVerification(principal.getSubject(), userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/verify-email")
    public ResponseEntity<AdminUserDtos.AdminUserView> verifyEmail(
            @AuthenticationPrincipal Jwt principal, @PathVariable String userId) {
        return ResponseEntity.ok(adminUserService.verifyEmail(principal.getSubject(), userId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal, @PathVariable String userId) {
        adminUserService.delete(principal.getSubject(), userId);
        return ResponseEntity.noContent().build();
    }
}
