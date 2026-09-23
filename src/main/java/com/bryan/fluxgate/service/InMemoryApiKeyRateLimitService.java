package com.bryan.fluxgate.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.bryan.fluxgate.exception.RateLimitExceededException;
import com.bryan.fluxgate.model.RateLimitCheckResponse;
import com.bryan.fluxgate.model.RateLimiteWindow;

@Service
public class InMemoryApiKeyRateLimitService implements ApiKeyRateLimitService {

    private static final int MAX_REQUESTS_PER_WINDOW = 5;

    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);

    private final ConcurrentHashMap<UUID, RateLimiteWindow> rateLimits = new ConcurrentHashMap<>();

    private final Clock clock;

    public InMemoryApiKeyRateLimitService() {
        this(Clock.systemUTC());
    }

    public InMemoryApiKeyRateLimitService(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public RateLimitCheckResponse validateAgainstLimit(UUID apiKeyId) {
        // Check capacity and claim a slot atomically for this key. Read time here
        // so a request waiting for another update uses its actual admission time.
        RateLimiteWindow updatedWindow = rateLimits.compute(apiKeyId, (key, existingWindow) -> {
            Instant now = clock.instant();
            if (existingWindow == null || isExpired(existingWindow, now)) {
                return new RateLimiteWindow(now, 1);
            }

            if (existingWindow.requestCount() >= MAX_REQUESTS_PER_WINDOW) {
                Instant resetAt = existingWindow.windowStart().plus(WINDOW_SIZE);
                Duration wait = Duration.between(now, resetAt);
                long retryAfterSeconds = wait.getSeconds() + (wait.getNano() > 0 ? 1 : 0);

                // Throwing from compute leaves the existing window unchanged.
                throw new RateLimitExceededException("Rate limit exceeded",
                        MAX_REQUESTS_PER_WINDOW, 0, resetAt, retryAfterSeconds);
            }

            return new RateLimiteWindow(existingWindow.windowStart(), existingWindow.requestCount() + 1);
        });

        return new RateLimitCheckResponse(true, MAX_REQUESTS_PER_WINDOW,
                MAX_REQUESTS_PER_WINDOW - updatedWindow.requestCount(),
                updatedWindow.windowStart().plus(WINDOW_SIZE), 0);
    }

    private boolean isExpired(RateLimiteWindow window, Instant now) {
        return !now.isBefore(window.windowStart().plus(WINDOW_SIZE));
    }
}
