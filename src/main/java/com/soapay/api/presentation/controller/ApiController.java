package com.soapay.api.presentation.controller;

import com.soapay.api.application.dto.request.CreateApiRequest;
import com.soapay.api.application.dto.response.ApiResponse;
import com.soapay.api.application.usecase.ApiService;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/apis")
public class ApiController {
    private final ApiService apiService;

    public ApiController(
            ApiService apiService) {
        this.apiService = apiService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse create(
            @Valid @RequestBody CreateApiRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            Authentication authentication) {
        return apiService.create(
                request, 
                key,
                authentication.getName());
    }

    @GetMapping("/{reference}")
    public ApiResponse get(
            @PathVariable String reference, 
            Authentication authentication) {
        return apiService.get(
                reference, 
                authentication.getName());
    }

    @GetMapping
    public List<ApiResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return apiService.list(
                authentication.getName(), 
                page, 
                size);
    }

    @PostMapping("/{reference}/cancel")
    public ApiResponse cancel(
            @PathVariable String reference, 
            Authentication authentication) {
        return apiService.cancel(
                reference, 
                authentication.getName());
    }

    @PostMapping("/{reference}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEST_OPERATOR')")
    public ApiResponse confirm(
            @PathVariable String reference) {
        return apiService.confirm(reference);
    }
}
