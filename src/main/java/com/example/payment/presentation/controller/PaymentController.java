package com.example.payment.presentation.controller;

import com.example.payment.application.dto.request.CreatePaymentRequest;
import com.example.payment.application.dto.response.PaymentResponse;
import com.example.payment.application.usecase.PaymentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request,
                                  @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                  Authentication authentication) {
        return paymentService.create(request, key, authentication.getName());
    }

    @GetMapping("/{reference}")
    public PaymentResponse get(@PathVariable String reference, Authentication authentication) {
        return paymentService.get(reference, authentication.getName());
    }

    @GetMapping
    public List<PaymentResponse> list(Authentication authentication) {
        return paymentService.list(authentication.getName());
    }

    @PostMapping("/{reference}/cancel")
    public PaymentResponse cancel(@PathVariable String reference, Authentication authentication) {
        return paymentService.cancel(reference, authentication.getName());
    }

    @PostMapping("/{reference}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_OPERATOR')")
    public PaymentResponse confirm(@PathVariable String reference) {
        return paymentService.confirm(reference);
    }
}
