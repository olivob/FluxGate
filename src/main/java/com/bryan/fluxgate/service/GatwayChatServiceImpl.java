package com.bryan.fluxgate.service;

import org.springframework.stereotype.Service;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.provider.ProviderClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class GatwayChatServiceImpl implements GatewayChatService {

    private final ProviderRoutingService providerRoutingService;

    @Override
    public ChatResponse createCompletion(ChatRequest request, ApiKeyPrincipal apiKeyPrincipal) {

        ProviderClient client = providerRoutingService.route(request, apiKeyPrincipal);

        return client.complete(request, apiKeyPrincipal);
    }
}
