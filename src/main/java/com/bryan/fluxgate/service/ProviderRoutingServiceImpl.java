package com.bryan.fluxgate.service;

import org.springframework.stereotype.Service;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.provider.MockProviderClient;
import com.bryan.fluxgate.provider.ProviderClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProviderRoutingServiceImpl implements ProviderRoutingService {

    private final MockProviderClient mockProviderClient;

    @Override
    public ProviderClient route(ChatRequest chatRequest, ApiKeyPrincipal principal) {
        // set attributes
        return mockProviderClient;
    }
}
