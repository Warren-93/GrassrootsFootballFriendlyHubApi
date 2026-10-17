package com.gffh.api.service;

import com.gffh.api.dto.NotificationDtos;
import com.gffh.api.model.NotificationPreference;
import com.gffh.api.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;

/** SCR-PR-09. One row per user, created lazily with every category on the first read or write. */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferences;

    public NotificationPreferenceService(NotificationPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    public NotificationDtos.PreferenceView get(String userId) {
        return toView(current(userId));
    }

    /**
     * A partial update: any flag left out keeps its current value rather than
     * turning off, so a client built before the email and push switches
     * existed, or one sending only the switch the user just flipped, changes
     * nothing else.
     */
    public NotificationDtos.PreferenceView update(String userId, NotificationDtos.UpdatePreferenceRequest request) {
        NotificationPreference current = current(userId);
        NotificationPreference saved = preferences.save(new NotificationPreference(userId,
                orCurrent(request.friendlyRequests(), current.friendlyRequests()),
                orCurrent(request.fixtures(), current.fixtures()),
                orCurrent(request.verification(), current.verification()),
                orCurrent(request.messages(), current.messages()),
                orCurrent(request.emailEnabled(), current.emailEnabled()),
                orCurrent(request.pushEnabled(), current.pushEnabled())));
        return toView(saved);
    }

    private NotificationPreference current(String userId) {
        return preferences.findByUserId(userId).orElseGet(() -> NotificationPreference.defaultsFor(userId));
    }

    private static boolean orCurrent(Boolean requested, boolean current) {
        return requested != null ? requested : current;
    }

    private NotificationDtos.PreferenceView toView(NotificationPreference p) {
        return new NotificationDtos.PreferenceView(p.friendlyRequests(), p.fixtures(), p.verification(), p.messages(),
                p.emailEnabled(), p.pushEnabled());
    }
}
