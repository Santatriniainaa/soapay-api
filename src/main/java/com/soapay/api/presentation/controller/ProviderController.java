package com.soapay.api.presentation.controller;

import com.soapay.api.application.usecase.ApiProviderRegistry;
import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.exception.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {
    private final ApiProviderRegistry providers;

    public ProviderController(ApiProviderRegistry providers) {
        this.providers = providers;
    }

    @GetMapping
    public List<ProviderResponse> list() {
        return providers.all().entrySet().stream()
            .map(entry -> new ProviderResponse(
                entry.getKey().name(),
                true,
                entry.getValue().isAvailable()))
            .toList();
    }

    @GetMapping("/{providerCode}")
    public ProviderResponse get(@PathVariable String providerCode) {
        ApiProvider provider = parseProvider(providerCode);
        return toResponse(provider);
    }

    @GetMapping("/{providerCode}/health")
    public ProviderResponse health(@PathVariable String providerCode) {
        ApiProvider provider = parseProvider(providerCode);
        return toResponse(provider);
    }

    private ProviderResponse toResponse(ApiProvider provider) {
        return new ProviderResponse(
                provider.name(),
                true,
                providers.get(provider).isAvailable()
            );
    }

    private static ApiProvider parseProvider(String value) {
        try {
            return ApiProvider.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(
                "UNKNOWN_PROVIDER",
                "Fournisseur inconnu", 
                404
            );
        }
    }

    public record ProviderResponse(String code, boolean simulated, boolean available) {
    }
}
