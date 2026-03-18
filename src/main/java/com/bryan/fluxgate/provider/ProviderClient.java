package com.bryan.fluxgate.provider;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;

public interface ProviderClient {
    ChatResponse complete(ChatRequest chatRequest, ApiKeyPrincipal principal);
}
