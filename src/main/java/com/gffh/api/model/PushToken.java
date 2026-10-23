package com.gffh.api.model;

import com.gffh.api.model.enums.PushPlatform;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One device registered for push notifications: an FCM registration token
 * (Android) or an APNs device token (iOS). A token identifies a device, not a
 * person, so it belongs to whoever registered it last - signing in as someone
 * else on the same phone moves it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PushToken {

    private String token;
    private String userId;
    private PushPlatform platform;
    private Instant registeredAt;
}
