package com.gffh.api.repository;

import com.gffh.api.domain.PushToken;

import java.util.List;

public interface PushTokenRepository {

    /** Inserts or replaces by token, so a device re-registering (or changing hands) never duplicates. */
    PushToken save(PushToken token);

    List<PushToken> findByUserId(String userId);

    /** Removes the token only while it belongs to {@code userId}, so nobody can unregister someone else's device. */
    void deleteForUser(String userId, String token);

    /** Removes the token whoever holds it - for tokens FCM or APNs report as no longer valid. */
    void delete(String token);

    void deleteByUserId(String userId);
}
