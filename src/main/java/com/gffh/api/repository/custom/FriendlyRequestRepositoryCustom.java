package com.gffh.api.repository.custom;

import com.gffh.api.model.FriendlyRequest;
import com.gffh.api.model.enums.RequestStatus;

import java.util.Optional;

/** Friendly request writes that a plain {@code save} can't express. */
public interface FriendlyRequestRepositoryCustom {

    /**
     * Saves a status change only if the stored request is still in
     * {@code expectedStatus}, so two parties acting at once can't both
     * succeed. Empty when someone else changed it first.
     */
    Optional<FriendlyRequest> saveIfStatus(FriendlyRequest request, RequestStatus expectedStatus);
}
