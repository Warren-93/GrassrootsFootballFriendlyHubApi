package com.gffh.api.service;

import com.gffh.api.domain.NotificationPreference;
import com.gffh.api.repository.NotificationPreferenceRepository;
import com.gffh.api.web.NotificationDtos;
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
     * The four category flags are always replaced. The email and push
     * switches are optional: a client that doesn't send them (one built
     * before they existed) leaves them as they were rather than turning them off.
     */
    public NotificationDtos.PreferenceView update(String userId, NotificationDtos.UpdatePreferenceRequest request) {
        NotificationPreference current = current(userId);
        NotificationPreference saved = preferences.save(new NotificationPreference(userId, request.friendlyRequests(),
                request.fixtures(), request.verification(), request.messages(),
                request.emailEnabled() != null ? request.emailEnabled() : current.emailEnabled(),
                request.pushEnabled() != null ? request.pushEnabled() : current.pushEnabled()));
        return toView(saved);
    }

    private NotificationPreference current(String userId) {
        return preferences.findByUserId(userId).orElseGet(() -> NotificationPreference.defaultsFor(userId));
    }

    private NotificationDtos.PreferenceView toView(NotificationPreference p) {
        return new NotificationDtos.PreferenceView(p.friendlyRequests(), p.fixtures(), p.verification(), p.messages(),
                p.emailEnabled(), p.pushEnabled());
    }
}
