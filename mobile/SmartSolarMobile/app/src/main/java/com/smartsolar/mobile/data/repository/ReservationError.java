package com.smartsolar.mobile.data.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates structured error information from API calls or network failures,
 * preserving ProblemDetails detail, traceId and field-level validation errors.
 */
public final class ReservationError {
    private final int statusCode;
    private final String message;
    private final String traceId;
    private final Map<String, List<String>> validationErrors;
    private final boolean sessionExpired;

    public ReservationError(int statusCode, String message, String traceId,
                            Map<String, List<String>> validationErrors, boolean sessionExpired) {
        this.statusCode = statusCode;
        this.message = message != null ? message : "An unexpected error occurred.";
        this.traceId = traceId;
        this.validationErrors = validationErrors != null ? Collections.unmodifiableMap(validationErrors) : Collections.emptyMap();
        this.sessionExpired = sessionExpired;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, List<String>> getValidationErrors() {
        return validationErrors;
    }

    public boolean isSessionExpired() {
        return sessionExpired;
    }
}
