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

    public static BusinessRuleException profileIncomplete(int percent) {
        return new BusinessRuleException("PROFILE_INCOMPLETE", HttpStatus.FORBIDDEN,
                "Your team profile is " + percent + "% complete. It must reach 80% "
                        + "before you can invite another team.");
    }

    public static BusinessRuleException duplicateRequest() {
        return new BusinessRuleException("DUPLICATE_REQUEST", HttpStatus.CONFLICT,
                "You already have an open request with this team.");
    }

    public static BusinessRuleException matchingFieldsLocked() {
        return new BusinessRuleException("MATCHING_FIELDS_LOCKED", HttpStatus.CONFLICT,
                "Age group and format cannot change once a fixture has been played.");
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
