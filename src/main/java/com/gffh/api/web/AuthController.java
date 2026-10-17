package com.gffh.api.web;

import com.gffh.api.domain.OAuthProvider;
import com.gffh.api.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Auth", description = "Account registration, Google and Apple sign-in, bearer-token issuance and account recovery")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies refreshCookies;

    public AuthController(AuthService authService, RefreshCookies refreshCookies) {
        this.authService = authService;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/api/v1/auth/register")
    public ResponseEntity<AuthDtos.TokenResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request,
                                                           HttpServletRequest http, HttpServletResponse response) {
        return ResponseEntity.ok(deliver(authService.register(request), http, response));
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<AuthDtos.TokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                                        HttpServletRequest http, HttpServletResponse response) {
        return ResponseEntity.ok(deliver(authService.login(request), http, response));
    }

    /**
     * Signs in with a Google ID token, creating the account on first use.
     * Answers exactly like login (cookie flow included), plus {@code newUser}.
     */
    @PostMapping("/api/v1/auth/oauth/google")
    public ResponseEntity<AuthDtos.TokenResponse> google(@Valid @RequestBody AuthDtos.OAuthSignInRequest request,
                                                         HttpServletRequest http, HttpServletResponse response) {
        return ResponseEntity.ok(deliver(authService.signInWith(OAuthProvider.GOOGLE, request), http, response));
    }

    /** As {@link #google}, with an Apple identity token. */
    @PostMapping("/api/v1/auth/oauth/apple")
    public ResponseEntity<AuthDtos.TokenResponse> apple(@Valid @RequestBody AuthDtos.OAuthSignInRequest request,
                                                        HttpServletRequest http, HttpServletResponse response) {
        return ResponseEntity.ok(deliver(authService.signInWith(OAuthProvider.APPLE, request), http, response));
    }

    /** Takes the refresh token from the body, or from the refresh cookie in the browser flow. */
    @PostMapping("/api/v1/auth/refresh")
    public ResponseEntity<AuthDtos.TokenResponse> refresh(
            @RequestBody(required = false) AuthDtos.RefreshRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        boolean cookieFlow = refreshCookies.applies(http);
        try {
            return ResponseEntity.ok(deliver(authService.refresh(presentedToken(request, http)), http, response));
        } catch (RuntimeException e) {
            if (cookieFlow) refreshCookies.clear(response);
            throw e;
        }
    }

    /** Revokes the refresh token so it can't be used again, and clears the refresh cookie. */
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) AuthDtos.RefreshRequest request,
                                       HttpServletRequest http, HttpServletResponse response) {
        authService.logout(presentedToken(request, http));
        if (refreshCookies.applies(http)) refreshCookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    private String presentedToken(AuthDtos.RefreshRequest request, HttpServletRequest http) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        return refreshCookies.applies(http) ? refreshCookies.read(http).orElse(null) : null;
    }

    private AuthDtos.TokenResponse deliver(AuthDtos.TokenResponse tokens, HttpServletRequest http,
                                           HttpServletResponse response) {
        if (!refreshCookies.applies(http)) return tokens;
        refreshCookies.write(response, tokens.refreshToken());
        return tokens.withoutRefreshToken();
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
        authService.changePassword(principal.getSubject(),
                principal.getClaimAsString(com.gffh.api.service.JwtService.SESSION_CLAIM), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/me/email")
    public ResponseEntity<AuthDtos.ChangeEmailResponse> changeEmail(@AuthenticationPrincipal Jwt principal,
                                                                     @Valid @RequestBody AuthDtos.ChangeEmailRequest request) {
        return ResponseEntity.ok(authService.changeEmail(principal.getSubject(), request));
    }
}
