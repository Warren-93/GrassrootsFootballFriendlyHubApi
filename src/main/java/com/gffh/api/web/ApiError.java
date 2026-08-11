package com.gffh.api.web;

/**
 * The structured error envelope defined in Technical Specification section 12.
 *
 * <p>The client maps {@code code} to user-facing copy (Volume 1, section 9.1);
 * {@code message} is a developer-facing fallback and is never rendered to a
 * manager verbatim. {@code requestId} is logged for support but must not be
 * shown in the interface.
 */
public record ApiError(Detail error) {

    public record Detail(String code, String message, String requestId) {}

    public static ApiError of(String code, String message, String requestId) {
        return new ApiError(new Detail(code, message, requestId));
    }
}
