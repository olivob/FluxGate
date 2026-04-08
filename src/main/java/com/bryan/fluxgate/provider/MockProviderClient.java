package com.bryan.fluxgate.provider;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.security.ApiRequestLogContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MockProviderClient implements ProviderClient {

    private final ApiRequestLogContext requestLogContext;

    @Override
    public ChatResponse complete(ChatRequest request, ApiKeyPrincipal principal) {
        requestLogContext.setProvider("mock-provider");
        requestLogContext.setModel(request.model());
        return new ChatResponse(
                UUID.randomUUID(),
                "mock-provider",
                request.model(),
                "Mock response for prompt: " + request.prompt() + "for key: " + principal.apiKeyId());
    }
}
