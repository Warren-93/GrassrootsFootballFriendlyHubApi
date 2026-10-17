package com.gffh.api.repository.mongo;

import com.gffh.api.model.PushToken;
import com.gffh.api.model.enums.PushPlatform;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Keyed by the token itself, so one device is only ever registered once. */
@Document("pushTokens")
public class PushTokenDocument {

    @Id
    public String token;
    public String userId;
    public PushPlatform platform;
    public Instant registeredAt;

    public static PushTokenDocument from(PushToken t) {
        PushTokenDocument d = new PushTokenDocument();
        d.token = t.token();
        d.userId = t.userId();
        d.platform = t.platform();
        d.registeredAt = t.registeredAt();
        return d;
    }

    public PushToken toDomain() {
        return new PushToken(token, userId, platform, registeredAt);
    }
}
