package com.soapay.api.presentation.advice;

import com.soapay.api.domain.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void apiExceptionPreservesPublicErrorDetails() {
        ApiException exception = new ApiException("NOT_FOUND", "Introuvable", 404);

        var response = handler.handleApi(exception, request());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().code());
        assertEquals("/api/v1/apis/PAY-123", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void missingHeaderIsReturnedAsBadRequest() throws Exception {
        Method method = TestEndpoint.class.getDeclaredMethod("endpoint", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MissingRequestHeaderException exception = new MissingRequestHeaderException("Idempotency-Key", parameter);

        var response = handler.handleValidation(exception, request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", response.getBody().code());
        assertEquals("En-tête requis manquant : Idempotency-Key", response.getBody().message());
    }

    @Test
    void malformedJsonIsReturnedAsBadRequest() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(
            "bad json", mock(HttpInputMessage.class));

        var response = handler.handleValidation(exception, request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Corps JSON invalide", response.getBody().message());
    }

    @Test
    void integrityConflictDoesNotExposeDatabaseDetails() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("secret sql detail");

        var response = handler.handleConflict(exception, request());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("DATA_INTEGRITY_CONFLICT", response.getBody().code());
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().message().contains("secret sql detail"));
    }

    @Test
    void unexpectedExceptionReturnsGenericInternalError() {
        var response = handler.handleUnexpected(new IllegalStateException("internal detail"), request());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().message().contains("internal detail"));
    }

    private static HttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/apis/PAY-123");
        return request;
    }

    @SuppressWarnings("unused")
    private static class TestEndpoint {
        void endpoint(@RequestHeader("Idempotency-Key") String key) {
        }
    }
}
