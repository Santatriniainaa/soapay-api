package com.example.payment.presentation.advice;

import com.example.payment.domain.exception.PaymentException;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiError> handlePayment(PaymentException exception, HttpServletRequest request) {
        ApiError error = new ApiError(
            Instant.now(),
            exception.getHttpStatus(),
            exception.getCode(),
            exception.getMessage(),
            request.getRequestURI());
        return ResponseEntity.status(exception.getHttpStatus()).body(error);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleValidation(Exception exception, HttpServletRequest request) {
        String message;
        if (exception instanceof MethodArgumentNotValidException validation) {
            message = validation.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Requête invalide");
        } else {
            message = "Corps JSON invalide";
        }

        ApiError error = new ApiError(
            Instant.now(), 400, "INVALID_REQUEST", message, request.getRequestURI());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        ApiError error = new ApiError(
            Instant.now(),
            409,
            "IDEMPOTENCY_OR_UNIQUENESS_CONFLICT",
            "Une opération identique ou conflictuelle existe déjà",
            request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        ApiError error = new ApiError(
            Instant.now(),
            500,
            "INTERNAL_ERROR",
            "Une erreur interne est survenue",
            request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

        public record ApiError(Instant timestamp, int status, String code, String message, String path) {
        }
}
