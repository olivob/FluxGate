package com.bryan.fluxgate.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bryan.fluxgate.config.SecurityConfig;
import com.bryan.fluxgate.exception.RateLimitExceededException;
import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.repository.ApiRequestLogRepository;
import com.bryan.fluxgate.security.ApiKeyAuthenticationProvider;
import com.bryan.fluxgate.service.ApiKeyAuthService;
import com.bryan.fluxgate.service.GatewayChatService;

@WebMvcTest({ApiKeyController.class, ChatController.class, HealthController.class})
@Import({SecurityConfig.class, ApiKeyAuthenticationProvider.class})
class ApiSecurityWebMvcTest {

    private static final String KEY = "test-key";
    private static final ApiKeyPrincipal PRINCIPAL = new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @Autowired MockMvc mvc;
    @MockitoBean ApiKeyAuthService authentication;
    @MockitoBean GatewayChatService gateway;
    @MockitoBean ApiRequestLogRepository requestLogs;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingOrBlankKeyReturnsJsonUnauthorized(String key) throws Exception {
        var request = get("/v1/verifyKey");
        if (key != null) {
            request.header("X-API-Key", key);
        }
        mvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("A valid X-API-Key header is required."));
        verifyNoInteractions(authentication, gateway, requestLogs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Invalid API key", "API key has expired", "API key has been revoked", "Account is not active"})
    void rejectedCredentialsUseTheSamePublicError(String reason) throws Exception {
        when(authentication.verifyApiKey(KEY)).thenThrow(new BadCredentialsException(reason));

        mvc.perform(get("/v1/verifyKey").header("X-API-Key", KEY))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("A valid X-API-Key header is required."));
        verify(authentication).verifyApiKey(KEY);
        verifyNoInteractions(gateway, requestLogs);
    }

    @Test
    void validKeyReachesControllerAndDoesNotAuthenticateTheNextRequest() throws Exception {
        when(authentication.verifyApiKey(KEY)).thenReturn(PRINCIPAL);

        mvc.perform(get("/v1/verifyKey").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(PRINCIPAL.accountId().toString()))
                .andExpect(jsonPath("$.apiKeyId").value(PRINCIPAL.apiKeyId().toString()));
        mvc.perform(get("/v1/verifyKey")).andExpect(status().isUnauthorized());

        verify(authentication).verifyApiKey(KEY);
        verify(requestLogs).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid-key"})
    void healthDoesNotRequireOrValidateCredentials(String key) throws Exception {
        var request = get("/health");
        if (key != null) {
            request.header("X-API-Key", key);
        }
        mvc.perform(request).andExpect(status().isOk()).andExpect(content().string("ok"));
        verifyNoInteractions(authentication, gateway, requestLogs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/metrics", "/actuator/info", "/admin", "/actuator/health/details"})
    void apiKeyDoesNotGrantAccessOutsideTheApi(String path) throws Exception {
        when(authentication.verifyApiKey(KEY)).thenReturn(PRINCIPAL);

        mvc.perform(get(path).header("X-API-Key", KEY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("Access to this resource is not permitted."));
        verifyNoInteractions(gateway);
    }

    @Test
    void metricsAreNotPublic() throws Exception {
        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void validChatRequestPassesTheAuthenticatedPrincipalToTheService() throws Exception {
        when(authentication.verifyApiKey(KEY)).thenReturn(PRINCIPAL);
        ChatRequest request = new ChatRequest("mock-model", "Hello");
        when(gateway.createCompletion(request, PRINCIPAL))
                .thenReturn(new ChatResponse(UUID.randomUUID(), "mock", "mock-model", "Hello back"));

        mvc.perform(post("/v1/chat/completions").header("X-API-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"model":"mock-model","prompt":"Hello"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output").value("Hello back"));
        verify(gateway).createCompletion(request, PRINCIPAL);
        verify(authentication).verifyApiKey(KEY);
    }

    @Test
    void rateLimitResponseSurvivesTheSecurityChain() throws Exception {
        when(authentication.verifyApiKey(KEY)).thenReturn(PRINCIPAL);
        when(gateway.createCompletion(any(), any())).thenThrow(new RateLimitExceededException(
                "Rate limit exceeded", 5, 0, Instant.parse("2026-01-01T12:01:00Z"), 42));

        mvc.perform(post("/v1/chat/completions").header("X-API-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"model":"mock-model","prompt":"Hello"}
                        """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.error").value("rate_limit_exceeded"));
    }

    @Test
    void invalidChatBodyIsRejectedBeforeCallingService() throws Exception {
        when(authentication.verifyApiKey(KEY)).thenReturn(PRINCIPAL);

        mvc.perform(post("/v1/chat/completions").header("X-API-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"model":"mock-model","prompt":""}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gateway);
    }
}
