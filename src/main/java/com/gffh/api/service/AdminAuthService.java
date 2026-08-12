package com.gffh.api.service;

import com.gffh.api.domain.PlatformAdmin;
import com.gffh.api.repository.PlatformAdminRepository;
import com.gffh.api.web.AdminAuthDtos;
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

    public AdminAuthService(PlatformAdminRepository admins, PasswordEncoder passwordEncoder,
                            TotpService totpService, AdminJwtService adminJwtService) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.adminJwtService = adminJwtService;
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
        String uri = totpService.provisioningUri(secret, admin.email(), "GFFH Admin");
        return new AdminAuthDtos.BootstrapResponse(admin.id(), secret, uri);
    }

    public AdminAuthDtos.LoginStep1Response login(AdminAuthDtos.LoginRequest request) {
        PlatformAdmin admin = admins.findByEmail(request.email())
                .filter(a -> passwordEncoder.matches(request.password(), a.passwordHash()))
                .orElseThrow(() -> new BusinessRuleException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                        "Email or password is incorrect."));
        return new AdminAuthDtos.LoginStep1Response(adminJwtService.issuePending2faToken(admin.id()));
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
        if (!totpService.verifyCode(admin.totpSecret(), request.code())) {
            throw new BusinessRuleException("INVALID_TOTP_CODE", HttpStatus.UNAUTHORIZED, "That code is incorrect.");
        }
        admins.save(new PlatformAdmin(admin.id(), admin.email(), admin.passwordHash(), admin.totpSecret(),
                admin.createdAt(), Instant.now()));
        String accessToken = adminJwtService.issueSessionToken(admin.id(), admin.email());
        return new AdminAuthDtos.SessionResponse(accessToken, "Bearer", adminJwtService.sessionTtlSeconds(), admin.email());
    }
}
