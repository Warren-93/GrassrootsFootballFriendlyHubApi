package com.gffh.api.repository;

import com.gffh.api.domain.NotificationPreference;

import java.util.Optional;

public interface NotificationPreferenceRepository {

    Optional<NotificationPreference> findByUserId(String userId);

    NotificationPreference save(NotificationPreference preference);
}
