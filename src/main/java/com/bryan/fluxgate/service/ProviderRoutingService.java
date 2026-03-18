package com.bryan.fluxgate.service;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.provider.ProviderClient;

public interface ProviderRoutingService {
    ProviderClient route(ChatRequest chatRequest, ApiKeyPrincipal principal);
}
