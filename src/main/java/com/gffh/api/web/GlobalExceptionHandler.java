package com.gffh.api.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.gffh.api.request.RequestStateMachine;
import com.gffh.api.service.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Translates exceptions into the structured error envelope.
 *
 * <p>Two rules matter here. Every response carries a request ID so that a
 * support report can be traced to a log line. And no message ever contains a
 * stack trace, a Mongo detail or a token - section 17 of the Technical
 * Specification forbids sensitive material in logs, and the same applies with
 * more force to responses.
 *
 * <p>Requests Spring rejects before a controller runs (unknown address, wrong
 * method, missing parameter, unreadable body) are the caller's mistake, not a
 * server fault: they get a 4xx and are never logged at ERROR.
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onMalformedRequest(HttpMessageNotReadableException ex) {
        // A client sent a body we can't parse - bad JSON, a missing field, or a
        // value that doesn't match one of our enums. That's a client error, not
        // a server crash, even though Jackson throws it from deep inside message
        // conversion before any controller method runs.
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", unreadableDetail(ex));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiError> onMissingInput(ServletRequestBindingException ex) {
        String detail = switch (ex) {
            case MissingServletRequestParameterException e -> e.getParameterName() + " is required.";
            case MissingPathVariableException e -> e.getVariableName() + " is required.";
            case MissingRequestHeaderException e -> "The " + e.getHeaderName() + " header is required.";
            default -> "The request is missing something it needs.";
        };
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<ApiError> onTypeMismatch(TypeMismatchException ex) {
        String name = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                (name == null ? "A value" : name) + " is not in the expected format.");
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> onUnknownRoute(Exception ex) {
        return respondQuietly(HttpStatus.NOT_FOUND, "NOT_FOUND", "That address doesn't exist.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> onMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ResponseEntity<ApiError> response = respondQuietly(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                ex.getMethod() + " isn't supported at this address.");
        Set<HttpMethod> allowed = ex.getSupportedHttpMethods();
        if (allowed == null || allowed.isEmpty()) return response;
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(allowed.toArray(HttpMethod[]::new))
                .body(response.getBody());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> onUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return respondQuietly(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Send the request body as JSON (Content-Type: application/json).");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> onNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        // The caller won't accept JSON, so there is no envelope it could read.
        log.info("Request failed [requestId={}] NOT_ACCEPTABLE", requestId());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(AccessDeniedException ex) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex) {
        // Any other client error Spring raises itself (it carries its own 4xx
        // status) is still the caller's mistake, not a crash.
        if (ex instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return respondQuietly(status, status == HttpStatus.BAD_REQUEST ? "VALIDATION_FAILED" : status.name(),
                    "The request couldn't be handled.");
        }
        String requestId = requestId();
        log.error("Unhandled exception [requestId={}]", requestId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR",
                        "Something went wrong. Please try again.", requestId));
    }

    /**
     * A short, safe description of why a body couldn't be read: the field
     * that's wrong when Jackson can say, never the raw parser message.
     */
    static String unreadableDetail(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause == null) return "The request body is missing.";
        if (cause instanceof MismatchedInputException m && !m.getPath().isEmpty()) {
            return path(m) + (m instanceof InvalidFormatException
                    ? ": unrecognised value." : ": wrong type or format.");
        }
        if (cause instanceof JsonMappingException) {
            return "The request body is invalid or contains an unrecognised value.";
        }
        if (cause instanceof JsonProcessingException) return "The request body isn't valid JSON.";
        return "The request body is invalid or contains an unrecognised value.";
    }

    private static String path(JsonMappingException ex) {
        StringBuilder out = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!out.isEmpty()) out.append('.');
                out.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                out.append('[').append(ref.getIndex()).append(']');
            }
        }
        return out.isEmpty() ? "A value" : out.toString();
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message) {
        String requestId = requestId();
        log.warn("Request failed [requestId={}] {}: {}", requestId, code, message);
        return ResponseEntity.status(status).body(ApiError.of(code, message, requestId));
    }

    /** For routing misses (wrong address, method or content type): worth a line, not a warning. */
    private ResponseEntity<ApiError> respondQuietly(HttpStatus status, String code, String message) {
        String requestId = requestId();
        log.info("Request failed [requestId={}] {}: {}", requestId, code, message);
        return ResponseEntity.status(status).body(ApiError.of(code, message, requestId));
    }

    private String requestId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        Object id = attrs.getAttribute("requestId", RequestAttributes.SCOPE_REQUEST);
        return id == null ? "unknown" : id.toString();
    }
}
