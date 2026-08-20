package com.gffh.api.web;

import com.gffh.api.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Auth", description = "Account registration, bearer-token issuance and account recovery")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<AuthDtos.TokenResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/api/v1/auth/refresh")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @GetMapping("/api/v1/me")
    public ResponseEntity<AuthDtos.UserView> me(@AuthenticationPrincipal Jwt principal) {
        return ResponseEntity.ok(authService.me(principal.getSubject()));
    }

    @PostMapping("/api/v1/auth/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody AuthDtos.PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/auth/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody AuthDtos.PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/auth/verify/resend")
    public ResponseEntity<AuthDtos.VerificationResendResponse> resendVerification(@AuthenticationPrincipal Jwt principal) {
        String token = authService.resendVerification(principal.getSubject());
        return ResponseEntity.ok(new AuthDtos.VerificationResendResponse(token));
    }

    @PostMapping("/api/v1/auth/verify/confirm")
    public ResponseEntity<Void> confirmVerification(@Valid @RequestBody AuthDtos.VerifyConfirmRequest request) {
        authService.confirmVerification(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Jwt principal,
                                                @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(principal.getSubject(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/me/email")
    public ResponseEntity<AuthDtos.ChangeEmailResponse> changeEmail(@AuthenticationPrincipal Jwt principal,
                                                                     @Valid @RequestBody AuthDtos.ChangeEmailRequest request) {
        return ResponseEntity.ok(authService.changeEmail(principal.getSubject(), request));
    }
}
