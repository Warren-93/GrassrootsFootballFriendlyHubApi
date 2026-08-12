package com.gffh.api.domain;

/** What kind of event a {@link Notification} is about - drives the icon/copy on HM-02's centre and PR-09's toggles. */
public enum NotificationType {
    REQUEST_RECEIVED(NotificationCategory.FRIENDLY_REQUESTS),
    REQUEST_ACCEPTED(NotificationCategory.FRIENDLY_REQUESTS),
    REQUEST_DECLINED(NotificationCategory.FRIENDLY_REQUESTS),
    REQUEST_CHANGES_REQUESTED(NotificationCategory.FRIENDLY_REQUESTS),
    REQUEST_WITHDRAWN(NotificationCategory.FRIENDLY_REQUESTS),
    FIXTURE_CONFIRMED(NotificationCategory.FIXTURES),
    FIXTURE_CANCELLED(NotificationCategory.FIXTURES),
    VERIFICATION_APPROVED(NotificationCategory.VERIFICATION),
    VERIFICATION_REJECTED(NotificationCategory.VERIFICATION),
    MESSAGE_RECEIVED(NotificationCategory.MESSAGES);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }
}
