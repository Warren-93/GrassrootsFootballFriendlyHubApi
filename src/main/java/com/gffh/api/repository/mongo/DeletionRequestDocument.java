package com.gffh.api.repository.mongo;

import com.gffh.api.model.DeletionRequest;
import com.gffh.api.model.enums.DeletionRequestStatus;
import com.gffh.api.model.enums.DeletionTargetType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("deletion_requests")
public class DeletionRequestDocument {

    @Id
    public String id;
    public String targetType;
    public String targetId;
    public String targetLabel;
    public String reason;
    public String status;
    public String requestedByAdminId;
    public String requestedByEmail;
    public Instant requestedAt;
    public String decidedByAdminId;
    public String decidedByEmail;
    public Instant decidedAt;
    public String decisionNote;
    public String summary;
    public String result;

    public static DeletionRequestDocument from(DeletionRequest r) {
        DeletionRequestDocument d = new DeletionRequestDocument();
        d.id = r.getId();
        d.targetType = r.getTargetType().name();
        d.targetId = r.getTargetId();
        d.targetLabel = r.getTargetLabel();
        d.reason = r.getReason();
        d.status = r.getStatus().name();
        d.requestedByAdminId = r.getRequestedByAdminId();
        d.requestedByEmail = r.getRequestedByEmail();
        d.requestedAt = r.getRequestedAt();
        d.decidedByAdminId = r.getDecidedByAdminId();
        d.decidedByEmail = r.getDecidedByEmail();
        d.decidedAt = r.getDecidedAt();
        d.decisionNote = r.getDecisionNote();
        d.summary = r.getSummary();
        d.result = r.getResult();
        return d;
    }

    public DeletionRequest toDomain() {
        return new DeletionRequest(id, DeletionTargetType.valueOf(targetType), targetId, targetLabel, reason,
                DeletionRequestStatus.valueOf(status), requestedByAdminId, requestedByEmail, requestedAt,
                decidedByAdminId, decidedByEmail, decidedAt, decisionNote, summary, result);
    }
}
