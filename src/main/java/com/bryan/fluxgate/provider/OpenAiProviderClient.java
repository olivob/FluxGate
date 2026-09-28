package com.bryan.fluxgate.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.bryan.fluxgate.config.OpenAiProperties;
import com.bryan.fluxgate.exception.ProviderException;
import com.bryan.fluxgate.exception.ProviderException.Kind;
import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.dto.ChatResponse;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.security.ApiRequestLogContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenAiProviderClient implements ProviderClient {
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final OpenAiProperties properties;
    private final ApiRequestLogContext logContext;
    private final URI endpoint;

    public OpenAiProviderClient(HttpClient http, ObjectMapper mapper, OpenAiProperties properties,
            ApiRequestLogContext logContext) {
        this(http, mapper, properties, logContext, URI.create("https://api.openai.com/v1/responses"));
    }

    // Package-private endpoint override for local HTTP contract tests only.
    OpenAiProviderClient(HttpClient http, ObjectMapper mapper, OpenAiProperties properties,
            ApiRequestLogContext logContext, URI endpoint) {
        this.http = http;
        this.mapper = mapper;
        this.properties = properties;
        this.logContext = logContext;
        this.endpoint = endpoint;
    }

    @Override
    public ChatResponse complete(ChatRequest request, ApiKeyPrincipal principal) {
        logContext.setProvider("openai");
        logContext.setModel(properties.getModel());
        String payload;
        try {
            payload = mapper.writeValueAsString(Map.of(
                    "model", properties.getModel(), "input", request.prompt(),
                    "max_output_tokens", properties.getMaxOutputTokens(), "store", false, "stream", false));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize provider request");
        }

        HttpRequest upstream = HttpRequest.newBuilder(endpoint)
                .timeout(properties.getRequestTimeout())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Client-Request-Id", logContext.getRequestId().toString())
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        var pending = http.sendAsync(upstream, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> response;
        try {
            // Bound the whole HTTP exchange, including receiving the response body.
            response = pending.get(properties.getRequestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new ProviderException(Kind.TIMEOUT);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderException(Kind.UNAVAILABLE);
        } catch (ExecutionException e) {
            throw new ProviderException(e.getCause() instanceof HttpTimeoutException ? Kind.TIMEOUT : Kind.UNAVAILABLE);
        }

        int status = response.statusCode();
        if (status == 429)
            throw new ProviderException(Kind.RATE_LIMITED);
        if (status == 401 || status == 403)
            throw new ProviderException(Kind.AUTHENTICATION_FAILED);
        if (status >= 500)
            throw new ProviderException(Kind.UNAVAILABLE);
        if (status < 200 || status >= 300)
            throw new ProviderException(Kind.REJECTED_REQUEST);

        return new ChatResponse(logContext.getRequestId(), "openai", request.model(), extractText(response.body()));
    }

    private String extractText(String body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new ProviderException(Kind.INVALID_RESPONSE);
        }
        if (root == null)
            throw new ProviderException(Kind.INVALID_RESPONSE);
        String status = root.path("status").asText();
        if ("incomplete".equals(status))
            throw new ProviderException(Kind.INCOMPLETE_RESPONSE);
        if (!"completed".equals(status) || !root.path("output").isArray()) {
            throw new ProviderException(Kind.INVALID_RESPONSE);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText()))
                continue;
            for (JsonNode part : item.path("content")) {
                if ("refusal".equals(part.path("type").asText()))
                    throw new ProviderException(Kind.REFUSAL);
                if ("output_text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
                    text.append(part.path("text").asText());
                }
            }
        }
        if (text.isEmpty())
            throw new ProviderException(Kind.INVALID_RESPONSE);
        return text.toString();
    }
}
