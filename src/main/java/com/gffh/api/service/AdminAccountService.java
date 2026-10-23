package com.gffh.api.service;

import com.gffh.api.dto.admin.AdminAccountDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.model.enums.AdminRole;
import com.gffh.api.repository.PlatformAdminRepository;
import com.gffh.api.security.TotpService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * The admin accounts themselves. Only the super admin adds or removes them,
 * and every account added here is an ordinary ADMIN - there is no way to make
 * a second super admin through the API.
 */
@Service
public class AdminAccountService {

    private final PlatformAdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final AdminAuthService adminAuthService;
    private final AuditLogService auditLogService;

    public AdminAccountService(PlatformAdminRepository admins, PasswordEncoder passwordEncoder,
                               TotpService totpService, AdminAuthService adminAuthService,
                               AuditLogService auditLogService) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.adminAuthService = adminAuthService;
        this.auditLogService = auditLogService;
    }

    public AdminAccountDtos.MeView me(String adminId) {
        PlatformAdmin admin = find(adminId);
        return new AdminAccountDtos.MeView(admin.getId(), admin.getEmail(), admin.effectiveRole().name(),
                adminAuthService.totpRequired());
    }

    public List<AdminAccountDtos.AdminAccountView> list(String actorId) {
        requireSuperAdmin(actorId);
        return admins.findAllByOrderByCreatedAtAsc().stream().map(AdminAccountDtos.AdminAccountView::from).toList();
    }

    public AdminAccountDtos.CreateAdminResponse create(String actorId, AdminAccountDtos.CreateAdminRequest request) {
        requireSuperAdmin(actorId);
        if (admins.findByEmail(request.email()).isPresent()) {
            throw new BusinessRuleException("ADMIN_EMAIL_TAKEN", HttpStatus.CONFLICT,
                    "There's already an admin account with that email.");
        }
        String secret = totpService.generateSecret();
        PlatformAdmin created = admins.save(new PlatformAdmin(null, request.email(),
                passwordEncoder.encode(request.password()), secret, Instant.now(), null, AdminRole.ADMIN));
        auditLogService.record(actorId, "ADMIN_CREATED", "ADMIN", created.getId(), created.getEmail());
        if (!adminAuthService.totpRequired()) {
            return new AdminAccountDtos.CreateAdminResponse(AdminAccountDtos.AdminAccountView.from(created), null, null);
        }
        return new AdminAccountDtos.CreateAdminResponse(AdminAccountDtos.AdminAccountView.from(created), secret,
                totpService.provisioningUri(secret, created.getEmail(), "Kickabout Admin"));
    }

    /**
     * The removed admin's current session token still works until it expires
     * (at most 30 minutes): admin tokens are checked by signature, not looked
     * up, the same as when a session simply times out.
     */
    public void delete(String actorId, String adminId) {
        requireSuperAdmin(actorId);
        PlatformAdmin target = find(adminId);
        if (target.isSuperAdmin()) {
            throw new BusinessRuleException("CANNOT_REMOVE_SUPER_ADMIN", HttpStatus.CONFLICT,
                    "The super admin account can't be removed.");
        }
        admins.deleteById(adminId);
        auditLogService.record(actorId, "ADMIN_REMOVED", "ADMIN", adminId, target.getEmail());
    }

    public boolean isSuperAdmin(String adminId) {
        return admins.findById(adminId).map(PlatformAdmin::isSuperAdmin).orElse(false);
    }

    public void requireSuperAdmin(String adminId) {
        if (!isSuperAdmin(adminId)) {
            throw new BusinessRuleException("SUPER_ADMIN_ONLY", HttpStatus.FORBIDDEN,
                    "Only the super admin can do this.");
        }
    }

    private PlatformAdmin find(String adminId) {
        return admins.findById(adminId).orElseThrow(() -> new BusinessRuleException(
                "ADMIN_NOT_FOUND", HttpStatus.NOT_FOUND, "That admin account could not be found."));
    }
}
