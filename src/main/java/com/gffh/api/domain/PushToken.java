package com.gffh.api.domain;

import java.time.Instant;

/**
 * One device registered for push notifications: an FCM registration token
 * (Android) or an APNs device token (iOS). A token identifies a device, not a
 * person, so it belongs to whoever registered it last - signing in as someone
 * else on the same phone moves it.
 */
public record PushToken(
        String token,
        String userId,
        PushPlatform platform,
        Instant registeredAt) {
}
