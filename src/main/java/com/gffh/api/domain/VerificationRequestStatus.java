package com.gffh.api.domain;

/**
 * AWAITING_SECOND_REJECTION exists because ADM-03 requires two-person review
 * for rejections: a first admin's reject moves here (recording their reason),
 * and only a second, different admin's reject finalizes it to REJECTED.
 */
public enum VerificationRequestStatus { PENDING, AWAITING_SECOND_REJECTION, APPROVED, REJECTED }
