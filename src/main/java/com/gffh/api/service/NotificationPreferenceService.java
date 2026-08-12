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
        NotificationPreference p = preferences.findByUserId(userId).orElseGet(() -> NotificationPreference.defaultsFor(userId));
        return toView(p);
    }

    public NotificationDtos.PreferenceView update(String userId, NotificationDtos.UpdatePreferenceRequest request) {
        NotificationPreference saved = preferences.save(new NotificationPreference(userId, request.friendlyRequests(),
                request.fixtures(), request.verification(), request.messages()));
        return toView(saved);
    }

    private NotificationDtos.PreferenceView toView(NotificationPreference p) {
        return new NotificationDtos.PreferenceView(p.friendlyRequests(), p.fixtures(), p.verification(), p.messages());
    }
}
