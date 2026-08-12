package com.gffh.api.repository.mongo;

import com.gffh.api.domain.NotificationPreference;
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

    public static NotificationPreferenceDocument from(NotificationPreference p) {
        NotificationPreferenceDocument d = new NotificationPreferenceDocument();
        d.userId = p.userId();
        d.friendlyRequests = p.friendlyRequests();
        d.fixtures = p.fixtures();
        d.verification = p.verification();
        d.messages = p.messages();
        return d;
    }

    public NotificationPreference toDomain() {
        return new NotificationPreference(userId, friendlyRequests, fixtures, verification, messages);
    }
}
