package com.gffh.api.service;

import com.gffh.api.model.Notification;
import com.gffh.api.model.NotificationPreference;
import com.gffh.api.model.Team;
import com.gffh.api.model.User;
import com.gffh.api.model.enums.NotificationCategory;
import com.gffh.api.model.enums.NotificationType;
import com.gffh.api.repository.NotificationPreferenceRepository;
import com.gffh.api.repository.TeamRepository;
import com.gffh.api.repository.UserRepository;
import com.gffh.api.service.email.EmailService;
import com.gffh.api.service.email.FixtureConfirmationEmails;
import com.gffh.api.service.push.PushMessage;
import com.gffh.api.service.push.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Carries each in-app notification beyond the app: by email and by push to
 * every device the manager has registered, each only while they have that
 * channel switched on (SCR-PR-09) and the category isn't muted.
 *
 * <p>Two types are never emailed: MESSAGE_RECEIVED (a chat would flood the
 * inbox) and FIXTURE_CONFIRMED ({@link FixtureConfirmationEmails} already
 * sends a fuller one). Both are still pushed.
 *
 * <p>Like {@link FixtureConfirmationEmails}, delivery happens off the request
 * thread and never fails the action that raised the notification.
 */
@Service
public class NotificationDelivery {

    private static final Logger log = LoggerFactory.getLogger(NotificationDelivery.class);
    static final Set<NotificationType> NOT_EMAILED =
            EnumSet.of(NotificationType.MESSAGE_RECEIVED, NotificationType.FIXTURE_CONFIRMED);

    private final EmailService email;
    private final PushService push;
    private final UserRepository users;
    private final TeamRepository teams;
    private final NotificationPreferenceRepository preferences;
    private final Executor executor;

    @Autowired
    public NotificationDelivery(EmailService email, PushService push, UserRepository users, TeamRepository teams,
                                NotificationPreferenceRepository preferences) {
        this(email, push, users, teams, preferences, Executors.newVirtualThreadPerTaskExecutor());
    }

    NotificationDelivery(EmailService email, PushService push, UserRepository users, TeamRepository teams,
                         NotificationPreferenceRepository preferences, Executor executor) {
        this.email = email;
        this.push = push;
        this.users = users;
        this.teams = teams;
        this.preferences = preferences;
        this.executor = executor;
    }

    /** Queues email and push for {@code notification}. Never throws. */
    public void deliver(Notification notification) {
        if (!email.isConfigured() && !push.isConfigured()) return;
        try {
            executor.execute(() -> {
                try {
                    deliverNow(notification);
                } catch (Exception e) {
                    log.warn("Could not deliver notification {}", notification.id(), e);
                }
            });
        } catch (Exception e) {
            log.warn("Could not queue delivery of notification {}", notification.id(), e);
        }
    }

    void deliverNow(Notification n) {
        User user = users.findById(n.userId()).orElse(null);
        if (user == null || user.suspended()) return;
        NotificationPreference preference = preferences.findByUserId(user.id())
                .orElseGet(() -> NotificationPreference.defaultsFor(user.id()));
        if (!preference.allows(n.type().category())) return;

        if (shouldEmail(n, user, preference)) {
            email.send(user.email(), n.title(), emailBody(n));
        }
        if (preference.pushEnabled() && push.isConfigured()) {
            push.sendToUser(user.id(), pushMessage(n));
        }
    }

    private boolean shouldEmail(Notification n, User user, NotificationPreference preference) {
        return email.isConfigured() && preference.emailEnabled() && user.emailVerified()
                && !NOT_EMAILED.contains(n.type());
    }

    String emailBody(Notification n) {
        String teamName = n.relatedTeamId() == null ? null
                : teams.findById(n.relatedTeamId()).map(Team::name).orElse(null);
        String why = teamName != null
                ? "You're getting this because you manage " + teamName + " on Kickabout."
                : "You're getting this because you manage a team on Kickabout.";
        return """
                %s

                %s

                Open it on Kickabout:
                %s

                %s You can turn these emails off in your notification settings.
                """.formatted(n.title(), n.body(), email.appLink(pathFor(n)), why);
    }

    /** The web app page a notification is about. */
    static String pathFor(Notification n) {
        if (n.relatedConversationId() != null) return "/messages/" + n.relatedConversationId();
        if (n.relatedFixtureId() != null) return "/fixtures/" + n.relatedFixtureId();
        if (n.relatedRequestId() != null) return "/request/" + n.relatedRequestId();
        if (n.type().category() == NotificationCategory.VERIFICATION && n.relatedTeamId() != null) {
            return "/team/" + n.relatedTeamId() + "/verification";
        }
        return "/notifications";
    }

    /** Title and body as in the app, plus the ids the app needs to open the right screen. */
    static PushMessage pushMessage(Notification n) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", n.type().name());
        putIfPresent(data, "requestId", n.relatedRequestId());
        putIfPresent(data, "fixtureId", n.relatedFixtureId());
        putIfPresent(data, "conversationId", n.relatedConversationId());
        putIfPresent(data, "teamId", n.relatedTeamId());
        return new PushMessage(n.title(), n.body(), data);
    }

    private static void putIfPresent(Map<String, String> data, String key, String value) {
        if (value != null) data.put(key, value);
    }
}
