package com.gffh.api.web;

import com.gffh.api.service.AuthService;
import com.gffh.api.service.BusinessRuleException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<AuthDtos.TokenResponse> register(
            @RequestHeader(value = RefreshTokenCookies.MODE_HEADER, required = false) String mode,
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        return cookies.respond(mode, authService.register(request));
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(
            @RequestHeader(value = RefreshTokenCookies.MODE_HEADER, required = false) String mode,
            @Valid @RequestBody AuthDtos.LoginRequest request) {
        return cookies.respond(mode, authService.login(request));
    }

    /** Mobile sends the refresh token in the body; the web client sends no body and relies on its cookie. */
    @PostMapping("/api/v1/auth/refresh")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(
            @RequestHeader(value = RefreshTokenCookies.MODE_HEADER, required = false) String mode,
            @CookieValue(value = RefreshTokenCookies.COOKIE_NAME, required = false) String cookieToken,
            @Valid @RequestBody(required = false) AuthDtos.RefreshRequest request) {
        String presented = RefreshTokenCookies.isCookieMode(mode) ? cookieToken
                : request != null ? request.refreshToken() : null;
        if (presented == null || presented.isBlank()) {
            throw new BusinessRuleException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                    "Your session has expired. Please sign in again.");
        }
        return cookies.respond(mode, authService.refresh(new AuthDtos.RefreshRequest(presented)));
    }

    /** Revokes the refresh token (cookie or body) and, for the web client, clears the cookie. */
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = RefreshTokenCookies.MODE_HEADER, required = false) String mode,
            @CookieValue(value = RefreshTokenCookies.COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) AuthDtos.RefreshRequest request) {
        if (RefreshTokenCookies.isCookieMode(mode)) {
            authService.logout(cookieToken);
            return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clearingCookie()).build();
        }
        authService.logout(request != null ? request.refreshToken() : null);
        return ResponseEntity.noContent().build();
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
        return ResponseEntity.ok(authService.resendVerification(principal.getSubject()));
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
