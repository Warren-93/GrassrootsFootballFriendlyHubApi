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
        d.id = r.id();
        d.targetType = r.targetType().name();
        d.targetId = r.targetId();
        d.targetLabel = r.targetLabel();
        d.reason = r.reason();
        d.status = r.status().name();
        d.requestedByAdminId = r.requestedByAdminId();
        d.requestedByEmail = r.requestedByEmail();
        d.requestedAt = r.requestedAt();
        d.decidedByAdminId = r.decidedByAdminId();
        d.decidedByEmail = r.decidedByEmail();
        d.decidedAt = r.decidedAt();
        d.decisionNote = r.decisionNote();
        d.summary = r.summary();
        d.result = r.result();
        return d;
    }

    public DeletionRequest toDomain() {
        return new DeletionRequest(id, DeletionTargetType.valueOf(targetType), targetId, targetLabel, reason,
                DeletionRequestStatus.valueOf(status), requestedByAdminId, requestedByEmail, requestedAt,
                decidedByAdminId, decidedByEmail, decidedAt, decisionNote, summary, result);
    }
}
