package com.gffh.api.service;

import org.springframework.http.HttpStatus;

/**
 * A rule from the specifications was violated. The {@code code} is one of the
 * documented error codes the client maps to user-facing copy (Volume 1,
 * section 9.1), so codes are part of the API contract and must not be renamed
 * without a client release.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessRuleException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }

    public HttpStatus status() { return status; }

    // ---- The documented codes, as factories so call sites stay readable -----

    public static BusinessRuleException notAvailable() {
        return new BusinessRuleException("FRIENDLY_REQUEST_NOT_ALLOWED", HttpStatus.CONFLICT,
                "The selected team is not available for the proposed date.");
    }

    public static BusinessRuleException availabilityConflict() {
        return new BusinessRuleException("AVAILABILITY_CONFLICT", HttpStatus.CONFLICT,
                "This team already has a fixture on this date.");
    }

    public static BusinessRuleException emailNotVerified() {
        return new BusinessRuleException("EMAIL_NOT_VERIFIED", HttpStatus.FORBIDDEN,
                "Verify your email address before sending invitations.");
    }

    public static BusinessRuleException clubAdminOnly() {
        return new BusinessRuleException("CLUB_ADMIN_ONLY", HttpStatus.FORBIDDEN,
                "Only a club admin can add, change or remove club admins.");
    }

    /**
     * Keeps the PROFILE_INCOMPLETE code clients already map, but the rule is
     * now "verified and has a venue" rather than a completeness percentage.
     */
    public static BusinessRuleException teamNotReady(boolean verified, boolean hasVenue) {
        String needed;
        if (!verified && !hasVenue) needed = "get your team verified and add a venue";
        else if (!verified) needed = "get your team verified";
        else needed = "add a venue";
        return new BusinessRuleException("PROFILE_INCOMPLETE", HttpStatus.FORBIDDEN,
                "Before you can send friendly requests, " + needed + ".");
    }

    public static BusinessRuleException recipientNotReady() {
        return new BusinessRuleException("RECIPIENT_NOT_READY", HttpStatus.CONFLICT,
                "That team can't receive friendly requests yet. They need to be verified and add a venue first.");
    }

    public static BusinessRuleException duplicateRequest() {
        return new BusinessRuleException("DUPLICATE_REQUEST", HttpStatus.CONFLICT,
                "You already have an open request with this team.");
    }

    public static BusinessRuleException matchingFieldsLocked() {
        return new BusinessRuleException("MATCHING_FIELDS_LOCKED", HttpStatus.CONFLICT,
                "Age group and format cannot change once a fixture has been played.");
    }

    public static BusinessRuleException invalidTimeRange() {
        return new BusinessRuleException("INVALID_TIME_RANGE", HttpStatus.BAD_REQUEST,
                "The end time must be after the start time.");
    }

    public static BusinessRuleException rateLimited() {
        return new BusinessRuleException("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS,
                "Too many attempts. Please try again later.");
    }

    public static BusinessRuleException blocked() {
        // Deliberately indistinguishable from "not found" so that blocking
        // cannot be detected by probing.
        return new BusinessRuleException("TEAM_NOT_FOUND", HttpStatus.NOT_FOUND,
                "That team could not be found.");
    }
}
