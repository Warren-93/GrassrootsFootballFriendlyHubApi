package com.gffh.api.repository.mongo;

import com.gffh.api.model.NotificationPreference;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("notificationPreferences")
public class NotificationPreferenceDocument {

    @Id
    public String userId;
    public boolean friendlyRequests;
    public boolean fixtures;
    public boolean verification;
    public boolean messages;
    /** Boxed: rows saved before these existed have neither, and both default to on. */
    public Boolean emailEnabled;
    public Boolean pushEnabled;

    public static NotificationPreferenceDocument from(NotificationPreference p) {
        NotificationPreferenceDocument d = new NotificationPreferenceDocument();
        d.userId = p.getUserId();
        d.friendlyRequests = p.isFriendlyRequests();
        d.fixtures = p.isFixtures();
        d.verification = p.isVerification();
        d.messages = p.isMessages();
        d.emailEnabled = p.isEmailEnabled();
        d.pushEnabled = p.isPushEnabled();
        return d;
    }

    public NotificationPreference toDomain() {
        return new NotificationPreference(userId, friendlyRequests, fixtures, verification, messages,
                emailEnabled == null || emailEnabled, pushEnabled == null || pushEnabled);
    }
}
