package com.gffh.api.model;

import com.gffh.api.model.enums.DeletionRequestStatus;
import com.gffh.api.model.enums.DeletionTargetType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * An admin's request to permanently delete a user or team, which only runs
 * once the super admin approves it. {@code summary} is what the preview
 * said would go when it was asked for; {@code result} what was actually
 * removed when it ran. Kept after the target is gone, as the record of who
 * asked, who approved and why.
 */
@Document("deletion_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeletionRequest {

    @Id
    private String id;
    private DeletionTargetType targetType;
    private String targetId;
    private String targetLabel;
    private String reason;
    private DeletionRequestStatus status;
    private String requestedByAdminId;
    private String requestedByEmail;
    @CreatedDate
    private Instant requestedAt;
    private String decidedByAdminId;
    private String decidedByEmail;
    private Instant decidedAt;
    private String decisionNote;
    private String summary;
    private String result;

    public DeletionRequest decided(DeletionRequestStatus newStatus, PlatformAdmin by, String note, String newResult) {
        return new DeletionRequest(id, targetType, targetId, targetLabel, reason, newStatus,
                requestedByAdminId, requestedByEmail, requestedAt, by.getId(), by.getEmail(), Instant.now(),
                note, summary, newResult);
    }
}
