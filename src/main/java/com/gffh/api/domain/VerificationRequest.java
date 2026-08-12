package com.gffh.api.domain;

import java.time.Instant;
import java.util.List;

/**
 * A club's request to have a team marked VERIFIED (SCR-PR-07 submission,
 * ADM-03 review queue). Evidence is stored as URLs, not binary uploads -
 * there's no file/blob storage service in this build; a real deployment
 * would need one before this is production-ready.
 */
public record VerificationRequest(
        String id,
        String teamId,
        String submittedByUserId,
        String affiliationNumber,
        String contactDetails,
        List<String> evidenceUrls,
        VerificationRequestStatus status,
        String firstRejectionAdminId,
        String firstRejectionReason,
        String finalRejectionReason,
        String reviewedByAdminId,
        Instant submittedAt,
        Instant reviewedAt) {
}
