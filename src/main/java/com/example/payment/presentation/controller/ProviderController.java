package com.example.payment.presentation.controller;

import com.example.payment.application.usecase.PaymentProviderRegistry;
import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.exception.PaymentException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
public class ProviderController {
    private final PaymentProviderRegistry providers;

    public ProviderController(PaymentProviderRegistry providers) {
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
        PaymentProvider provider = parseProvider(providerCode);
        return toResponse(provider);
    }

    @GetMapping("/{providerCode}/health")
    public ProviderResponse health(@PathVariable String providerCode) {
        PaymentProvider provider = parseProvider(providerCode);
        return toResponse(provider);
    }

    private ProviderResponse toResponse(PaymentProvider provider) {
        return new ProviderResponse(
                provider.name(),
                true,
                providers.get(provider).isAvailable()
            );
    }

    private static PaymentProvider parseProvider(String value) {
        try {
            return PaymentProvider.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new PaymentException(
                "UNKNOWN_PROVIDER",
                "Fournisseur inconnu", 
                404
            );
        }
    }

    public record ProviderResponse(String code, boolean simulated, boolean available) {
    }
}
