package com.bryan.fluxgate.provider;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;

@Service
public class MockProviderClient implements ProviderClient {

    @Override
    public ChatResponse complete(ChatRequest request, ApiKeyPrincipal principal) {
        return new ChatResponse(
                UUID.randomUUID(),
                "mock-provider",
                request.model(),
                "Mock response for prompt: " + request.prompt());
    }
}
