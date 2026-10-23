package com.gffh.api.service;

import com.gffh.api.dto.admin.AdminUserDtos;
import com.gffh.api.exception.BusinessRuleException;
import com.gffh.api.model.User;
import com.gffh.api.model.enums.VerificationTokenPurpose;
import com.gffh.api.repository.AdminActivityRepository;
import com.gffh.api.repository.MembershipRepository;
import com.gffh.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserService {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final VerificationTokenService verificationTokenService;
    private final AuditLogService auditLogService;
    private final MemberInviteService memberInvites;
    private final RefreshTokenService refreshTokenService;
    private final AdminActivityRepository activity;

    public AdminUserService(UserRepository users, MembershipRepository memberships,
                            VerificationTokenService verificationTokenService, AuditLogService auditLogService,
                            MemberInviteService memberInvites,
                            RefreshTokenService refreshTokenService, AdminActivityRepository activity) {
        this.users = users;
        this.memberships = memberships;
        this.verificationTokenService = verificationTokenService;
        this.auditLogService = auditLogService;
        this.memberInvites = memberInvites;
        this.refreshTokenService = refreshTokenService;
        this.activity = activity;
    }

    public List<AdminUserDtos.AdminUserView> search(String query) {
        List<User> found = users.search(query, 500);
        Map<String, Instant> lastActive = activity.lastActiveAt(found.stream().map(User::getId).toList());
        return found.stream()
                .map(u -> AdminUserDtos.AdminUserView.from(u, memberships.findByUserId(u.getId()), lastActive.get(u.getId())))
                .toList();
    }

    public AdminUserDtos.AdminUserView get(String userId) {
        User user = find(userId);
        return AdminUserDtos.AdminUserView.from(user, memberships.findByUserId(userId),
                activity.lastActiveAt(List.of(userId)).get(userId));
    }

    /** Signs the user out everywhere too, so no session outlives the suspension by more than an access token. */
    public AdminUserDtos.AdminUserView suspend(String adminId, String userId, String reason) {
        User user = find(userId);
        users.save(withSuspended(user, true));
        refreshTokenService.revokeAll(userId);
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
        verificationTokenService.issue(user.getId(), VerificationTokenPurpose.PASSWORD_RESET, user.getEmail());
        auditLogService.record(adminId, "USER_PASSWORD_RESET_FORCED", "USER", userId, null);
    }

    public void resendVerification(String adminId, String userId) {
        User user = find(userId);
        if (!user.isEmailVerified()) {
            verificationTokenService.issue(user.getId(), VerificationTokenPurpose.EMAIL_VERIFY, user.getEmail());
        }
        auditLogService.record(adminId, "USER_VERIFICATION_RESENT", "USER", userId, null);
    }

    /**
     * A manual bypass for the self-serve email-verification link (see
     * VerificationTokenService's javadoc: there's no real email provider in
     * this build) - lets an admin unblock an account directly instead of
     * relying on the account holder following the link themselves. Like the
     * link, it accepts any team invites waiting for the address.
     */
    public AdminUserDtos.AdminUserView verifyEmail(String adminId, String userId) {
        User user = find(userId);
        memberInvites.acceptPendingInvites(users.save(withEmailVerified(user, true)));
        auditLogService.record(adminId, "USER_EMAIL_VERIFIED_BY_ADMIN", "USER", userId, null);
        return get(userId);
    }

    private User find(String userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessRuleException(
                "USER_NOT_FOUND", HttpStatus.NOT_FOUND, "That account could not be found."));
    }

    private static User withSuspended(User user, boolean suspended) {
        return new User(user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName(),
                user.isEmailVerified(), user.getCreatedAt(), suspended);
    }

    private static User withEmailVerified(User user, boolean verified) {
        return new User(user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName(),
                verified, user.getCreatedAt(), user.isSuspended());
    }
}
