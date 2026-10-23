package com.gffh.api.repository.custom;

import com.gffh.api.model.DeletionRequest;
import com.gffh.api.model.enums.DeletionRequestStatus;

import java.util.Optional;

/** Deletion request writes that a derived query method can't express. */
public interface DeletionRequestRepositoryCustom {

    /**
     * Saves {@code updated} only if the stored request is still in
     * {@code expected}, so two admins acting on one request at once can't
     * both win. Empty if it had already moved on.
     */
    Optional<DeletionRequest> saveIfStatus(DeletionRequest updated, DeletionRequestStatus expected);
}
