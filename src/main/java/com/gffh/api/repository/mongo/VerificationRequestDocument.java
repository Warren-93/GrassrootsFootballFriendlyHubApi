package com.gffh.api.repository.mongo;

import com.gffh.api.model.VerificationRequest;
import com.gffh.api.model.enums.VerificationRequestStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document("verification_requests")
public class VerificationRequestDocument {

    @Id
    public String id;
    public String teamId;
    public String submittedByUserId;
    public String affiliationNumber;
    public String contactDetails;
    public List<String> evidenceUrls;
    public String status;
    public String firstRejectionAdminId;
    public String firstRejectionReason;
    public String finalRejectionReason;
    public String reviewedByAdminId;
    public Instant submittedAt;
    public Instant reviewedAt;

    public static VerificationRequestDocument from(VerificationRequest r) {
        VerificationRequestDocument d = new VerificationRequestDocument();
        d.id = r.getId();
        d.teamId = r.getTeamId();
        d.submittedByUserId = r.getSubmittedByUserId();
        d.affiliationNumber = r.getAffiliationNumber();
        d.contactDetails = r.getContactDetails();
        d.evidenceUrls = r.getEvidenceUrls();
        d.status = r.getStatus().name();
        d.firstRejectionAdminId = r.getFirstRejectionAdminId();
        d.firstRejectionReason = r.getFirstRejectionReason();
        d.finalRejectionReason = r.getFinalRejectionReason();
        d.reviewedByAdminId = r.getReviewedByAdminId();
        d.submittedAt = r.getSubmittedAt();
        d.reviewedAt = r.getReviewedAt();
        return d;
    }

    public VerificationRequest toDomain() {
        return new VerificationRequest(id, teamId, submittedByUserId, affiliationNumber, contactDetails,
                evidenceUrls, VerificationRequestStatus.valueOf(status), firstRejectionAdminId,
                firstRejectionReason, finalRejectionReason, reviewedByAdminId, submittedAt, reviewedAt);
    }
}
