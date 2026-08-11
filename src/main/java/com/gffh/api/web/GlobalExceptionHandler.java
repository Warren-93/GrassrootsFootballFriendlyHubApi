package com.gffh.api.web;

import com.gffh.api.request.RequestStateMachine;
import com.gffh.api.service.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.stream.Collectors;

/**
 * Translates exceptions into the structured error envelope.
 *
 * <p>Two rules matter here. Every response carries a request ID so that a
 * support report can be traced to a log line. And no message ever contains a
 * stack trace, a Mongo detail or a token - section 17 of the Technical
 * Specification forbids sensitive material in logs, and the same applies with
 * more force to responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RequestStateMachine.IllegalRequestTransition.class)
    public ResponseEntity<ApiError> onIllegalTransition(
            RequestStateMachine.IllegalRequestTransition ex) {
        // 409: the request moved underneath the caller, or the caller is not
        // entitled to act. The client refreshes rather than retrying blindly.
        return respond(HttpStatus.CONFLICT, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> onBusinessRule(BusinessRuleException ex) {
        return respond(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        String requestId = requestId();
        log.error("Unhandled exception [requestId={}]", requestId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR",
                        "Something went wrong. Please try again.", requestId));
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message) {
        String requestId = requestId();
        log.warn("Request failed [requestId={}] {}: {}", requestId, code, message);
        return ResponseEntity.status(status).body(ApiError.of(code, message, requestId));
    }

    private String requestId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        Object id = attrs.getAttribute("requestId", RequestAttributes.SCOPE_REQUEST);
        return id == null ? "unknown" : id.toString();
    }
}
