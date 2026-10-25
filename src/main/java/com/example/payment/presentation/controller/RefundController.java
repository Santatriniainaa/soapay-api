package com.example.payment.presentation.controller;

import com.example.payment.application.dto.request.CreateRefundRequest;
import com.example.payment.application.dto.response.RefundResponse;
import com.example.payment.application.usecase.RefundService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RefundController {
    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/payments/{reference}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse create(
            @PathVariable String reference,
            @Valid @RequestBody CreateRefundRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            Authentication authentication) {
        return refundService.create(reference, request, key, authentication.getName());
    }

    @GetMapping("/payments/{reference}/refunds")
    public List<RefundResponse> list(@PathVariable String reference, Authentication authentication) {
        return refundService.list(reference, authentication.getName());
    }

    @GetMapping("/refunds/{refundReference}")
    public RefundResponse get(@PathVariable String refundReference, Authentication authentication) {
        return refundService.get(refundReference, authentication.getName());
    }
}
