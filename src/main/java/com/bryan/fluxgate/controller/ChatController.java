package com.bryan.fluxgate.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.service.GatewayChatService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequestMapping("/v1/chat")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final GatewayChatService gatewayChatService;

    @PostMapping(value = "/completions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> createCompletion(@Valid @RequestBody ChatRequest chatRequest,
            Authentication authentication) {
        ApiKeyPrincipal principal = (ApiKeyPrincipal) authentication.getPrincipal();

        ChatResponse response = gatewayChatService.createCompletion(chatRequest, principal);

        return ResponseEntity.ok().body(response);
    }
}
