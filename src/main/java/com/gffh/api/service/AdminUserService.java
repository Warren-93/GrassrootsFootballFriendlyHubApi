package com.gffh.api.service;

import com.gffh.api.domain.User;
import com.gffh.api.domain.VerificationTokenPurpose;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.web.AdminUserDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final VerificationTokenService verificationTokenService;
    private final AuditLogService auditLogService;
    private final PrivacyService privacyService;

    public AdminUserService(UserRepository users, MembershipRepository memberships,
                            VerificationTokenService verificationTokenService, AuditLogService auditLogService,
                            PrivacyService privacyService) {
        this.users = users;
        this.memberships = memberships;
        this.verificationTokenService = verificationTokenService;
        this.auditLogService = auditLogService;
        this.privacyService = privacyService;
    }

    public List<AdminUserDtos.AdminUserView> search(String query) {
        return users.search(query, 500).stream()
                .map(u -> AdminUserDtos.AdminUserView.from(u, memberships.findByUserId(u.id())))
                .toList();
    }

    public AdminUserDtos.AdminUserView get(String userId) {
        User user = find(userId);
        return AdminUserDtos.AdminUserView.from(user, memberships.findByUserId(userId));
    }

    public AdminUserDtos.AdminUserView suspend(String adminId, String userId, String reason) {
        User user = find(userId);
        users.save(withSuspended(user, true));
        auditLogService.record(adminId, "USER_SUSPENDED", "USER", userId, reason);
        return get(userId);
    }

    public AdminUserDtos.AdminUserView unsuspend(String adminId, String userId, String reason) {
        User user = find(userId);
        users.save(withSuspended(user, false));
        auditLogService.record(adminId, "USER_UNSUSPENDED", "USER", userId, reason);
        return get(userId);
    }

    public void forcePasswordReset(String adminId, String userId) {
        User user = find(userId);
        verificationTokenService.issue(user.id(), VerificationTokenPurpose.PASSWORD_RESET, user.email());
        auditLogService.record(adminId, "USER_PASSWORD_RESET_FORCED", "USER", userId, null);
    }

    public void resendVerification(String adminId, String userId) {
        User user = find(userId);
        if (!user.emailVerified()) {
            verificationTokenService.issue(user.id(), VerificationTokenPurpose.EMAIL_VERIFY, user.email());
        }
        auditLogService.record(adminId, "USER_VERIFICATION_RESENT", "USER", userId, null);
    }

    /**
     * A manual bypass for the self-serve email-verification link (see
     * VerificationTokenService's javadoc: there's no real email provider in
     * this build) - lets an admin unblock an account directly instead of
     * relying on the account holder following the link themselves.
     */
    public AdminUserDtos.AdminUserView verifyEmail(String adminId, String userId) {
        User user = find(userId);
        users.save(withEmailVerified(user, true));
        auditLogService.record(adminId, "USER_EMAIL_VERIFIED_BY_ADMIN", "USER", userId, null);
        return get(userId);
    }

    /**
     * Reuses PrivacyService.deleteAccount for the actual removal (and its
     * "not the sole CLUB_ADMIN of any club" guard) so an admin-initiated
     * delete can't orphan a club any more than a self-service one can. The
     * guard's message is written for the self-service caller ("your
     * account"), so it's reworded here for an admin deleting someone else's.
     */
    public void delete(String adminId, String userId) {
        User user = find(userId);
        try {
            privacyService.deleteAccount(userId);
        } catch (BusinessRuleException ex) {
            if ("LAST_CLUB_ADMIN".equals(ex.code())) {
                throw new BusinessRuleException(ex.code(), ex.status(), user.displayName()
                        + " is the only admin for at least one club - promote someone else there first.");
            }
            throw ex;
        }
        auditLogService.record(adminId, "USER_DELETED", "USER", userId, "Deleted by admin (was " + user.email() + ")");
    }

    private User find(String userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND, "That account could not be found."));
    }

    private static User withSuspended(User user, boolean suspended) {
        return new User(user.id(), user.email(), user.passwordHash(), user.displayName(),
                user.emailVerified(), user.createdAt(), suspended);
    }

    private static User withEmailVerified(User user, boolean verified) {
        return new User(user.id(), user.email(), user.passwordHash(), user.displayName(),
                verified, user.createdAt(), user.suspended());
    }
}
