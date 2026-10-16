package com.gffh.api.push;

import com.gffh.api.domain.PushPlatform;

/** Delivers one push to one device through the platform's own service (FCM or APNs). */
public interface PushSender {

    PushPlatform platform();

    /** False until the sender's credentials are configured; an unconfigured sender is never called. */
    boolean isConfigured();

    /** Sends synchronously. Never throws: every failure is reported as a {@link Result}. */
    Result send(String deviceToken, PushMessage message);

    enum Result {
        SENT,
        /** The service says this token will never work again; the caller should forget it. */
        INVALID_TOKEN,
        /** Anything else (outage, bad credentials, rate limit). The token is kept. */
        FAILED
    }
}
