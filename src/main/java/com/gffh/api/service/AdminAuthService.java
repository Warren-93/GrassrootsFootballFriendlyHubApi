package com.gffh.api.service;

import com.gffh.api.config.RateLimitProperties;
import com.gffh.api.dto.admin.AdminAuthDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.repository.PlatformAdminRepository;
import com.gffh.api.security.AdminJwtService;
import com.gffh.api.security.RateLimiter;
import com.gffh.api.security.TotpService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AdminAuthService {

    private final PlatformAdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final AdminJwtService adminJwtService;
    private final RateLimiter rateLimiter;
    private final RateLimitProperties rateLimits;
    private final boolean totpRequired;

    public AdminAuthService(PlatformAdminRepository admins, PasswordEncoder passwordEncoder,
                            TotpService totpService, AdminJwtService adminJwtService,
                            RateLimiter rateLimiter, RateLimitProperties rateLimits,
                            @Value("${gffh.admin.totp-required:false}") boolean totpRequired) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.adminJwtService = adminJwtService;
        this.rateLimiter = rateLimiter;
        this.rateLimits = rateLimits;
        this.totpRequired = totpRequired;
    }

    /**
     * Creates the first (and, through this endpoint, only) admin account.
     * There is no public admin registration - every account after the first
     * is created by an existing admin through ADM-06 Users.
     */
    public AdminAuthDtos.BootstrapResponse bootstrap(AdminAuthDtos.BootstrapRequest request) {
        if (admins.count() > 0) {
            throw new BusinessRuleException("ADMIN_ALREADY_BOOTSTRAPPED", HttpStatus.CONFLICT,
                    "An administrator account already exists. Ask an existing admin to create your account.");
        }
        String secret = totpService.generateSecret();
        PlatformAdmin admin = admins.save(new PlatformAdmin(null, request.email(),
                passwordEncoder.encode(request.password()), secret, Instant.now(), null));
        if (!totpRequired) return new AdminAuthDtos.BootstrapResponse(admin.id(), null, null);
        String uri = totpService.provisioningUri(secret, admin.email(), "Kickabout Admin");
        return new AdminAuthDtos.BootstrapResponse(admin.id(), secret, uri);
    }

    /** Failed attempts are capped per email each hour, at the same limit as user sign-in. */
    public AdminAuthDtos.LoginStep1Response login(AdminAuthDtos.LoginRequest request) {
        String limitKey = "admin-auth:" + request.email().toLowerCase();
        if (rateLimiter.isExhausted(limitKey, rateLimits.getAuthAttemptsPerHour())) {
            throw BusinessRuleException.rateLimited();
        }
        PlatformAdmin admin = admins.findByEmail(request.email())
                .filter(a -> passwordEncoder.matches(request.password(), a.passwordHash()))
                .orElseThrow(() -> {
                    rateLimiter.record(limitKey);
                    return new BusinessRuleException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                            "Email or password is incorrect.");
                });
        String pendingToken = adminJwtService.issuePending2faToken(admin.id());
        if (totpRequired) return new AdminAuthDtos.LoginStep1Response(pendingToken, null, null, null, null);
        AdminAuthDtos.SessionResponse session = startSession(admin);
        return new AdminAuthDtos.LoginStep1Response(pendingToken, session.accessToken(), session.tokenType(),
                session.expiresIn(), session.email());
    }

    public AdminAuthDtos.SessionResponse verify(AdminAuthDtos.VerifyRequest request) {
        String adminId;
        try {
            adminId = adminJwtService.verifyPending2faToken(request.pendingToken());
        } catch (Exception e) {
            throw new BusinessRuleException("INVALID_PENDING_TOKEN", HttpStatus.UNAUTHORIZED,
                    "Your sign-in session has expired. Please sign in again.");
        }
        PlatformAdmin admin = admins.findById(adminId).orElseThrow(() -> new BusinessRuleException(
                "ADMIN_NOT_FOUND", HttpStatus.UNAUTHORIZED, "Your sign-in session has expired. Please sign in again."));
        if (totpRequired && !totpService.verifyCode(admin.totpSecret(), request.code())) {
            throw new BusinessRuleException("INVALID_TOTP_CODE", HttpStatus.UNAUTHORIZED, "That code is incorrect.");
        }
        return startSession(admin);
    }

    private AdminAuthDtos.SessionResponse startSession(PlatformAdmin admin) {
        admins.save(new PlatformAdmin(admin.id(), admin.email(), admin.passwordHash(), admin.totpSecret(),
                admin.createdAt(), Instant.now()));
        String accessToken = adminJwtService.issueSessionToken(admin.id(), admin.email());
        return new AdminAuthDtos.SessionResponse(accessToken, "Bearer", adminJwtService.sessionTtlSeconds(), admin.email());
    }
}
