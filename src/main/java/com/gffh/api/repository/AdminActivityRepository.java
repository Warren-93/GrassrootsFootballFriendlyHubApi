package com.gffh.api.repository;

import com.gffh.api.model.Block;
import com.gffh.api.model.MemberInvite;
import com.gffh.api.model.Message;
import com.gffh.api.model.RefreshToken;
import com.gffh.api.model.Report;
import com.gffh.api.model.VerificationRequest;
import com.gffh.api.model.VerificationToken;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Read-only lookups for the admin site's user and team detail pages - the
 * "who did what, when" queries no screen in the apps needs, so they live
 * here rather than on each type's own repository. Lists are newest first.
 */
public interface AdminActivityRepository {

    /** Most recent sign-in or session refresh per user; users with none are left out. */
    Map<String, Instant> lastActiveAt(Collection<String> userIds);

    List<RefreshToken> sessionsFor(String userId, int limit);

    List<VerificationToken> emailLinksFor(String userId, int limit);

    List<Message> messagesSentBy(String userId, int limit);

    long messagesIn(Collection<String> conversationIds);

    List<String> requestIdsCreatedBy(String userId, int limit);

    List<VerificationRequest> verificationRequestsSubmittedBy(String userId);

    List<VerificationRequest> verificationRequestsFor(String teamId);

    List<MemberInvite> invitesSentBy(String userId, int limit);

    List<Report> reportsInvolving(String teamId);

    List<Block> blocksInvolving(String teamId);

    long notificationsFor(String userId);

    long availabilitySlotsFor(String teamId);
}
