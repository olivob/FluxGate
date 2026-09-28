package com.bryan.fluxgate.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.provider.MockProviderClient;
import com.bryan.fluxgate.provider.ProviderClient;
import com.bryan.fluxgate.provider.OpenAiProviderClient;
import com.bryan.fluxgate.exception.ProviderException;
import com.bryan.fluxgate.exception.UnsupportedModelException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProviderRoutingServiceImpl implements ProviderRoutingService {

    private final MockProviderClient mockProviderClient;
    private final ObjectProvider<OpenAiProviderClient> openAiProvider;

    @Override
    public ProviderClient route(ChatRequest chatRequest, ApiKeyPrincipal principal) {
        return switch (chatRequest.model()) {
            case "mock-model" -> mockProviderClient;
            case "openai" -> {
                OpenAiProviderClient client = openAiProvider.getIfAvailable();
                if (client == null)
                    throw new ProviderException(ProviderException.Kind.NOT_CONFIGURED);
                yield client;
            }
            default -> throw new UnsupportedModelException();
        };
    }
}
