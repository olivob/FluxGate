package com.bryan.fluxgate.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import com.bryan.fluxgate.config.OpenAiProperties;
import com.bryan.fluxgate.exception.ProviderException;
import com.bryan.fluxgate.exception.ProviderException.Kind;
import com.bryan.fluxgate.model.RequestAttributeKeys;
import com.bryan.fluxgate.model.dto.ChatRequest;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.security.ApiRequestLogContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

class OpenAiProviderClientTest {
    private static final String SUCCESS = """
            {"id":"resp_upstream","status":"completed","output":[
              {"type":"reasoning","summary":[]},
              {"type":"message","content":[{"type":"output_text","text":"Hello"},
                {"type":"output_text","text":" world"}]}]}
            """;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> correlation = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> payload = new AtomicReference<>();
    private final CountDownLatch received = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final UUID requestId = UUID.randomUUID();
    private volatile int upstreamStatus = 200;
    private volatile String upstreamBody = SUCCESS;
    private volatile boolean delayBody;
    private HttpServer server;
    private ExecutorService executor;
    private HttpClient http;
    private OpenAiProperties properties;

    @BeforeEach
    void startLocalUpstream() throws IOException {
        properties = new OpenAiProperties();
        properties.setApiKey("test-provider-key");
        properties.setModel("configured-upstream-model");
        properties.setRequestTimeout(Duration.ofSeconds(5));
        request.setAttribute(RequestAttributeKeys.REQUEST_ID, requestId);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/v1/responses", exchange -> {
            calls.incrementAndGet();
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            correlation.set(exchange.getRequestHeaders().getFirst("X-Client-Request-Id"));
            payload.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            received.countDown();
            byte[] body = upstreamBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(upstreamStatus, body.length);
                if (delayBody) release.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Cancellation in the timeout test may close the connection before the write.
            } finally {
                exchange.close();
            }
        });
        server.start();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    }

    @AfterEach
    void closeResources() {
        release.countDown();
        server.stop(0);
        http.shutdownNow();
        executor.shutdownNow();
    }

    @Test
    void sendsTheProviderContractAndNormalizesTextWithoutAssumingFirstOutputIsAMessage() throws Exception {
        var result = complete();
        assertEquals("POST", method.get());
        assertEquals("Bearer test-provider-key", authorization.get());
        assertEquals(requestId.toString(), correlation.get());
        JsonNode sent = mapper.readTree(payload.get());
        assertEquals("configured-upstream-model", sent.path("model").asText());
        assertEquals("Hello", sent.path("input").asText());
        assertEquals(1024, sent.path("max_output_tokens").asInt());
        assertFalse(sent.path("store").asBoolean(true));
        assertFalse(sent.path("stream").asBoolean(true));
        assertEquals(requestId, result.requestId());
        assertEquals("openai", result.provider());
        assertEquals("openai", result.model());
        assertEquals("Hello world", result.output());
        assertEquals("openai", request.getAttribute(RequestAttributeKeys.PROVIDER));
        assertEquals("configured-upstream-model", request.getAttribute(RequestAttributeKeys.MODEL));
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @CsvSource({"429,RATE_LIMITED", "401,AUTHENTICATION_FAILED", "403,AUTHENTICATION_FAILED",
            "500,UNAVAILABLE", "503,UNAVAILABLE", "400,REJECTED_REQUEST", "302,REJECTED_REQUEST"})
    void classifiesHttpFailuresWithoutRetryingOrRetainingUpstreamBody(int status, Kind kind) {
        upstreamStatus = status;
        upstreamBody = "secret upstream error detail";
        ProviderException error = assertThrows(ProviderException.class, this::complete);
        assertEquals(kind, error.getKind());
        assertFalse(error.toString().contains(upstreamBody));
        assertNull(error.getCause());
        assertEquals(1, calls.get());
        assertEquals("configured-upstream-model", request.getAttribute(RequestAttributeKeys.MODEL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not JSON", "null", "{}", "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":42}]}]}"})
    void rejectsMalformedOrMissingText(String body) {
        upstreamBody = body;
        assertEquals(Kind.INVALID_RESPONSE, assertThrows(ProviderException.class, this::complete).getKind());
    }

    @Test
    void incompleteOutputIsNotReportedAsSuccessfulCompletion() {
        upstreamBody = SUCCESS.replace("completed", "incomplete");
        assertEquals(Kind.INCOMPLETE_RESPONSE, assertThrows(ProviderException.class, this::complete).getKind());
    }

    @Test
    void refusalIsClassifiedSeparately() {
        upstreamBody = """
                {"status":"completed","output":[{"type":"message","content":[
                {"type":"refusal","refusal":"Cannot comply"}]}]}
                """;
        assertEquals(Kind.REFUSAL, assertThrows(ProviderException.class, this::complete).getKind());
    }

    @Test
    void timeoutIncludesAStalledResponseBodyAndDoesNotRetry() throws Exception {
        properties.setRequestTimeout(Duration.ofMillis(500));
        delayBody = true;
        assertEquals(Kind.TIMEOUT, assertThrows(ProviderException.class, this::complete).getKind());
        assertTrue(received.await(1, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    void connectionFailureIsUnavailable() {
        server.stop(0);
        assertEquals(Kind.UNAVAILABLE, assertThrows(ProviderException.class, this::complete).getKind());
    }

    private com.bryan.fluxgate.model.dto.ChatResponse complete() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses");
        var client = new OpenAiProviderClient(http, mapper, properties, new ApiRequestLogContext(request), endpoint);
        return client.complete(new ChatRequest("openai", "Hello"),
                new ApiKeyPrincipal(UUID.randomUUID(), UUID.randomUUID()));
    }
}
