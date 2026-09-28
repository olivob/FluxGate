package com.bryan.fluxgate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bryan.fluxgate.exception.RateLimitExceededException;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.service.GatewayChatService;

class GlobalExceptionHandlerTest {

    @ParameterizedTest
    @CsvSource({ "1, second", "42, seconds" })
    void chatRateLimitFailureProducesActionableJsonAndRetryHeader(long delay, String unit) throws Exception {
        Instant resetAt = Instant.parse("2026-01-01T12:01:00Z");
        GatewayChatService service = (request, principxal) -> {
            throw new RateLimitExceededException("Internal limiter message", 25, 0, resetAt, delay);
        };
        var mvc = MockMvcBuilders.standaloneSetup(new ChatController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        var principal = new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());

        mvc.perform(post("/v1/chat/completions")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("""
                        {"model":"mock-model","prompt":"Hello"}
                        """))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, Long.toString(delay)))
                .andExpect(jsonPath("$.error").value("rate_limit_exceeded"))
                .andExpect(jsonPath("$.message").value("Request limit reached. Retry in " + delay + " " + unit + "."))
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.remaining").value(0))
                .andExpect(jsonPath("$.resetAt").value(resetAt.toString()))
                .andExpect(jsonPath("$.retryAfterSeconds").value(delay));
    }
}
