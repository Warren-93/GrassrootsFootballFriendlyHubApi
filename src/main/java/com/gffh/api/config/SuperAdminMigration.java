package com.gffh.api.config;

import com.gffh.api.model.PlatformAdmin;
import com.gffh.api.model.enums.AdminRole;
import com.gffh.api.repository.PlatformAdminRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Admin accounts saved before roles existed have none. If no super admin
 * exists yet, the oldest admin account - the one made through the bootstrap
 * step - becomes it, and every other account stays an ordinary admin.
 * Idempotent: once a super admin exists each startup is a single read.
 */
@Component
public class SuperAdminMigration {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminMigration.class);

    private final PlatformAdminRepository admins;

    public SuperAdminMigration(PlatformAdminRepository admins) {
        this.admins = admins;
    }

    @PostConstruct
    public void migrate() {
        List<PlatformAdmin> all = admins.findAll();
        if (all.isEmpty() || all.stream().anyMatch(PlatformAdmin::isSuperAdmin)) return;
        PlatformAdmin oldest = all.get(0);
        admins.save(oldest.withRole(AdminRole.SUPER_ADMIN));
        log.info("Made the oldest admin account ({}) the super admin", oldest.getEmail());
    }
}
