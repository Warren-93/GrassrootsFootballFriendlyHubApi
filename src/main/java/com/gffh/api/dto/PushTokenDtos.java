package com.gffh.api.dto;

import com.gffh.api.model.enums.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Wire types for registering a device for push notifications. */
public final class PushTokenDtos {

    private PushTokenDtos() {}

    /** {@code token} is the FCM registration token (ANDROID) or the APNs device token in hex (IOS). */
    public record RegisterPushTokenRequest(@NotNull PushPlatform platform, @NotBlank @Size(max = 4096) String token) {}

    public record DeletePushTokenRequest(@NotBlank @Size(max = 4096) String token) {}
}
