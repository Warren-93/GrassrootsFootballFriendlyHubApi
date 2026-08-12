package com.gffh.api.repository;

import com.gffh.api.domain.Notification;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(String id);

    List<Notification> findByUserId(String userId, int limit);

    long countUnread(String userId);
}
