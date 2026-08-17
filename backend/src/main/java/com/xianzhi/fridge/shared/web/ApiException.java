package com.xianzhi.fridge.shared.web;

import java.util.Map;
import java.time.Duration;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of(), null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        this(status, code, message, fields, null);
    }

    private ApiException(HttpStatus status, String code, String message, Map<String, String> fields,
                         Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException rateLimited(Duration retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many login attempts",
                Map.of(), retryAfter.toSeconds());
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public Map<String, String> getFields() { return fields; }
    public Long getRetryAfterSeconds() { return retryAfterSeconds; }
}
