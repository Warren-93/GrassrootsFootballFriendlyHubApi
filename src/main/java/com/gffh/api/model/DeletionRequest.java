package com.gffh.api.model;

import com.gffh.api.model.enums.DeletionRequestStatus;
import com.gffh.api.model.enums.DeletionTargetType;

import java.time.Instant;

/**
 * An admin's request to permanently delete a user or team, which only runs
 * once the super admin approves it. {@code summary} is what the preview
 * said would go when it was asked for; {@code result} what was actually
 * removed when it ran. Kept after the target is gone, as the record of who
 * asked, who approved and why.
 */
public record DeletionRequest(
        String id,
        DeletionTargetType targetType,
        String targetId,
        String targetLabel,
        String reason,
        DeletionRequestStatus status,
        String requestedByAdminId,
        String requestedByEmail,
        Instant requestedAt,
        String decidedByAdminId,
        String decidedByEmail,
        Instant decidedAt,
        String decisionNote,
        String summary,
        String result) {

    public DeletionRequest decided(DeletionRequestStatus newStatus, PlatformAdmin by, String note, String newResult) {
        return new DeletionRequest(id, targetType, targetId, targetLabel, reason, newStatus,
                requestedByAdminId, requestedByEmail, requestedAt, by.id(), by.email(), Instant.now(),
                note, summary, newResult);
    }
}
