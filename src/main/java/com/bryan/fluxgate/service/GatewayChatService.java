package com.bryan.fluxgate.service;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;

public interface GatewayChatService {
    ChatResponse createCompletion(ChatRequest chatRequest, ApiKeyPrincipal principal);
}
