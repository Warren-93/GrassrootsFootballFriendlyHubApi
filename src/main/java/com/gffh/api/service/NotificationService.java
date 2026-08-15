package com.gffh.api.service;

import com.gffh.api.domain.Notification;
import com.gffh.api.domain.NotificationPreference;
import com.gffh.api.domain.NotificationType;
import com.gffh.api.repository.NotificationPreferenceRepository;
import com.gffh.api.repository.NotificationRepository;
import com.gffh.api.web.NotificationDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * SCR-HM-02's notification centre. Fans a single event out to every manager of
 * a team, skipping anyone who has muted that event's category (SCR-PR-09).
 *
 * <p>Deliberately defensive: creating a notification is a side effect of some
 * other action succeeding (a request was sent, a fixture confirmed, ...), so a
 * failure here must never surface as a failure of that action. Every fan-out
 * call catches and logs rather than propagating.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_LISTED = 100;

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final MembershipService membershipService;

    public NotificationService(NotificationRepository notifications, NotificationPreferenceRepository preferences,
                               MembershipService membershipService) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.membershipService = membershipService;
    }

    public void notifyTeam(String teamId, NotificationType type, String title, String body,
                           String relatedRequestId, String relatedFixtureId) {
        notifyTeam(teamId, type, title, body, relatedRequestId, relatedFixtureId, null);
    }

    public void notifyTeam(String teamId, NotificationType type, String title, String body,
                           String relatedRequestId, String relatedFixtureId, String relatedConversationId) {
        try {
            for (String userId : membershipService.managerUserIdsFor(teamId)) {
                if (!preferenceFor(userId).allows(type.category())) continue;
                notifications.save(new Notification(null, userId, type, title, body, teamId,
                        relatedRequestId, relatedFixtureId, relatedConversationId, false, Instant.now()));
            }
        } catch (Exception e) {
            log.warn("Failed to fan out notification type={} teamId={}", type, teamId, e);
        }
    }

    public List<NotificationDtos.NotificationView> list(String userId) {
        return notifications.findByUserId(userId, MAX_LISTED).stream()
                .map(NotificationDtos.NotificationView::from)
                .toList();
    }

    public long unreadCount(String userId) {
        return notifications.countUnread(userId);
    }

    public void markRead(String userId, String id) {
        Notification n = notifications.findById(id).orElseThrow(() -> new BusinessRuleException(
                "NOTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND, "That notification could not be found."));
        if (!n.userId().equals(userId)) {
            throw new BusinessRuleException("NOTIFICATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "That notification could not be found.");
        }
        if (!n.read()) {
            notifications.save(new Notification(n.id(), n.userId(), n.type(), n.title(), n.body(),
                    n.relatedTeamId(), n.relatedRequestId(), n.relatedFixtureId(), n.relatedConversationId(),
                    true, n.createdAt()));
        }
    }

    public void markAllRead(String userId) {
        notifications.findByUserId(userId, MAX_LISTED).stream()
                .filter(n -> !n.read())
                .forEach(n -> markRead(userId, n.id()));
    }

    public void clearAll(String userId) {
        notifications.deleteAllForUser(userId);
    }

    private NotificationPreference preferenceFor(String userId) {
        return preferences.findByUserId(userId).orElseGet(() -> NotificationPreference.defaultsFor(userId));
    }
}
