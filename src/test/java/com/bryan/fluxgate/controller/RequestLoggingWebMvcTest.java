package com.bryan.fluxgate.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.bryan.fluxgate.config.SecurityConfig;
import com.bryan.fluxgate.entity.ApiRequestLog;
import com.bryan.fluxgate.exception.RateLimitExceededException;
import com.bryan.fluxgate.exception.ProviderException;
import com.bryan.fluxgate.exception.UnsupportedModelException;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.repository.ApiRequestLogRepository;
import com.bryan.fluxgate.security.ApiKeyAuthenticationProvider;
import com.bryan.fluxgate.service.ApiKeyAuthService;
import com.bryan.fluxgate.service.GatewayChatService;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, ApiKeyAuthenticationProvider.class})
class RequestLoggingWebMvcTest {

    private static final ApiKeyPrincipal PRINCIPAL = new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID());
    private static final String BODY = """
            {"model":"mock-model","prompt":"Hello"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean ApiKeyAuthService authentication;
    @MockitoBean GatewayChatService gateway;
    @MockitoBean ApiRequestLogRepository logs;

    @BeforeEach
    void authenticate() {
        when(authentication.verifyApiKey("test-key")).thenReturn(PRINCIPAL);
    }

    @Test
    void successLogsTheGeneratedHeaderIdAndRequestMetadata() throws Exception {
        when(gateway.createCompletion(any(), any())).thenReturn(
                new ChatResponse(UUID.randomUUID(), "mock", "mock-model", "Hello back"));
        MvcResult result = mvc.perform(post("/v1/chat/completions")
                .header("X-API-Key", "test-key").header("X-Request-ID", "untrusted-client-id")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andReturn();

        ApiRequestLog log = capturedLog(result, 200);
        assertEquals("/v1/chat/completions", log.getPath());
        assertEquals("POST", log.getMethod());
        assertNull(log.getErrorCode());
        assertNotNull(log.getRequestedAt());
        assertNotNull(log.getCompletedAt());
        assertTrue(log.getLatencyMs() >= 0);
    }

    @Test
    void rateLimitFailureIsLoggedAs429WithAnErrorCode() throws Exception {
        when(gateway.createCompletion(any(), any())).thenThrow(new RateLimitExceededException(
                "Rate limit exceeded", 5, 0, Instant.parse("2026-01-01T12:01:00Z"), 42));

        MvcResult result = chat(BODY).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42")).andReturn();
        assertEquals("rate_limit_exceeded", capturedLog(result, 429).getErrorCode());
    }

    @ParameterizedTest
    @CsvSource({"TIMEOUT,504,upstream_timeout", "RATE_LIMITED,503,upstream_rate_limited",
            "AUTHENTICATION_FAILED,502,upstream_authentication_failed", "UNAVAILABLE,503,upstream_unavailable",
            "INVALID_RESPONSE,502,upstream_invalid_response", "NOT_CONFIGURED,503,upstream_not_configured",
            "REJECTED_REQUEST,502,upstream_rejected_request", "INCOMPLETE_RESPONSE,502,upstream_incomplete_response",
            "REFUSAL,422,upstream_refusal"})
    void providerFailuresHaveDistinctHttpAndAuditCodes(ProviderException.Kind kind, int status, String code)
            throws Exception {
        when(gateway.createCompletion(any(), any())).thenThrow(new ProviderException(kind));
        MvcResult result = chat(BODY).andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(code))
                .andExpect(header().doesNotExist("Retry-After")).andReturn();
        assertEquals(code, capturedLog(result, status).getErrorCode());
    }

    @Test
    void unknownModelIsAClientError() throws Exception {
        when(gateway.createCompletion(any(), any())).thenThrow(new UnsupportedModelException());
        MvcResult result = chat(BODY).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_model")).andReturn();
        assertEquals("unsupported_model", capturedLog(result, 400).getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"model\":\"mock-model\",\"prompt\":\"\"}"})
    void malformedOrInvalidBodyRemainsA400AndIsLogged(String body) throws Exception {
        MvcResult result = chat(body).andExpect(status().isBadRequest()).andReturn();
        assertEquals("http_400", capturedLog(result, 400).getErrorCode());
        verifyNoInteractions(gateway);
    }

    @Test
    void unexpectedFailureIsLoggedAs500WithoutExposingExceptionDetails() throws Exception {
        when(gateway.createCompletion(any(), any())).thenThrow(new IllegalStateException("secret upstream detail"));

        MvcResult result = chat(BODY).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_error"))
                .andExpect(jsonPath("$.message").value(
                        "An unexpected error occurred. Contact support with the X-Request-ID."))
                .andReturn();
        assertEquals("internal_error", capturedLog(result, 500).getErrorCode());
        assertFalse(result.getResponse().getContentAsString().contains("secret upstream detail"));
    }

    @Test
    void failedLogWriteDoesNotReplaceSuccessfulResponse() throws Exception {
        when(gateway.createCompletion(any(), any())).thenReturn(
                new ChatResponse(UUID.randomUUID(), "mock", "mock-model", "Hello back"));
        when(logs.save(any())).thenThrow(new IllegalStateException("Database unavailable"));

        chat(BODY).andExpect(status().isOk()).andExpect(jsonPath("$.output").value("Hello back"));
        verify(logs).save(any());
    }

    @Test
    void rejectedKeyGetsARequestIdWithoutAnUnattributedDatabaseRow() throws Exception {
        when(authentication.verifyApiKey("test-key")).thenThrow(new BadCredentialsException("Invalid API key"));
        MvcResult result = chat(BODY).andExpect(status().isUnauthorized()).andReturn();
        assertNotNull(UUID.fromString(result.getResponse().getHeader("X-Request-ID")));
        verifyNoInteractions(logs, gateway);
    }

    private org.springframework.test.web.servlet.ResultActions chat(String body) throws Exception {
        return mvc.perform(post("/v1/chat/completions").header("X-API-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ApiRequestLog capturedLog(MvcResult result, int status) {
        ArgumentCaptor<ApiRequestLog> captor = ArgumentCaptor.forClass(ApiRequestLog.class);
        verify(logs).save(captor.capture());
        ApiRequestLog log = captor.getValue();
        assertEquals(UUID.fromString(result.getResponse().getHeader("X-Request-ID")), log.getId());
        assertEquals(PRINCIPAL.accountId(), log.getAccountId());
        assertEquals(PRINCIPAL.apiKeyId(), log.getApiKeyId());
        assertEquals(status, log.getStatusCode());
        return log;
    }
}
