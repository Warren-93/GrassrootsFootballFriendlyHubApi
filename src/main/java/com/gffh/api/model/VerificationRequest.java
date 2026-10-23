package com.gffh.api.model;

import com.gffh.api.model.enums.VerificationRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * A club's request to have a team marked VERIFIED (SCR-PR-07 submission,
 * ADM-03 review queue). Evidence is stored as URLs, not binary uploads -
 * there's no file/blob storage service in this build; a real deployment
 * would need one before this is production-ready.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    private String id;
    private String teamId;
    private String submittedByUserId;
    private String affiliationNumber;
    private String contactDetails;
    private List<String> evidenceUrls;
    private VerificationRequestStatus status;
    private String firstRejectionAdminId;
    private String firstRejectionReason;
    private String finalRejectionReason;
    private String reviewedByAdminId;
    private Instant submittedAt;
    private Instant reviewedAt;
}
