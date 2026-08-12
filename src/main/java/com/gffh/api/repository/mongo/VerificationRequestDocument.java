package com.gffh.api.repository.mongo;

import com.gffh.api.domain.VerificationRequest;
import com.gffh.api.domain.VerificationRequestStatus;
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
        d.id = r.id();
        d.teamId = r.teamId();
        d.submittedByUserId = r.submittedByUserId();
        d.affiliationNumber = r.affiliationNumber();
        d.contactDetails = r.contactDetails();
        d.evidenceUrls = r.evidenceUrls();
        d.status = r.status().name();
        d.firstRejectionAdminId = r.firstRejectionAdminId();
        d.firstRejectionReason = r.firstRejectionReason();
        d.finalRejectionReason = r.finalRejectionReason();
        d.reviewedByAdminId = r.reviewedByAdminId();
        d.submittedAt = r.submittedAt();
        d.reviewedAt = r.reviewedAt();
        return d;
    }

    public VerificationRequest toDomain() {
        return new VerificationRequest(id, teamId, submittedByUserId, affiliationNumber, contactDetails,
                evidenceUrls, VerificationRequestStatus.valueOf(status), firstRejectionAdminId,
                firstRejectionReason, finalRejectionReason, reviewedByAdminId, submittedAt, reviewedAt);
    }
}
