package com.xianzhi.fridge.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {
    @Test
    void missingResourceIsReturnedAsNotFoundInsteadOfInternalError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/api/v1/disabled"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(ApiEnvelope.error("NOT_FOUND", "Resource not found", Map.<String, Object>of()));
    }
}
