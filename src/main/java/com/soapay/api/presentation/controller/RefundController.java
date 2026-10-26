package com.soapay.api.presentation.controller;

import com.soapay.api.application.dto.request.CreateRefundRequest;
import com.soapay.api.application.dto.response.RefundResponse;
import com.soapay.api.application.usecase.RefundService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RefundController {
    private final RefundService refundService;

    public RefundController(
            RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/apis/{reference}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse create(
            @PathVariable String reference,
            @Valid @RequestBody CreateRefundRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            Authentication authentication) {
        return refundService.create(reference, request, key, authentication.getName());
    }

    @GetMapping("/apis/{reference}/refunds")
    public List<RefundResponse> list(@PathVariable String reference,
                                    Authentication authentication,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return refundService.list(
                reference, 
                authentication.getName(), 
                page, 
                size);
    }

    @GetMapping("/refunds/{refundReference}")
    public RefundResponse get(
            @PathVariable String refundReference, 
            Authentication authentication) {
        return refundService.get(
                refundReference, 
                authentication.getName());
    }
}
