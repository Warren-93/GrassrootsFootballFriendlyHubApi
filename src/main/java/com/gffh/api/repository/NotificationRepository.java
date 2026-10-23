package com.gffh.api.repository;

import com.gffh.api.model.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Limit limit);

    long countByUserIdAndReadFalse(String userId);

    void deleteByUserId(String userId);
}
