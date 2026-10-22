package com.gffh.api.repository;

import com.gffh.api.model.DeletionRequest;
import com.gffh.api.model.enums.DeletionRequestStatus;
import com.gffh.api.model.enums.DeletionTargetType;

import java.util.List;
import java.util.Optional;

public interface DeletionRequestRepository {

    Optional<DeletionRequest> findById(String id);

    Optional<DeletionRequest> findPendingFor(DeletionTargetType targetType, String targetId);

    /** Newest first; all statuses when {@code status} is null. */
    List<DeletionRequest> list(DeletionRequestStatus status, int limit);

    DeletionRequest save(DeletionRequest request);

    /**
     * Saves {@code updated} only if the stored request is still in
     * {@code expected}, so two admins acting on one request at once can't
     * both win. Empty if it had already moved on.
     */
    Optional<DeletionRequest> saveIfStatus(DeletionRequest updated, DeletionRequestStatus expected);
}
