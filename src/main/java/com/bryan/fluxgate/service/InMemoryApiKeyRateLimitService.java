package com.bryan.fluxgate.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.bryan.fluxgate.exception.RateLimitExceededException;
import com.bryan.fluxgate.model.RateLimiteWindow;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class InMemoryApiKeyRateLimitService implements ApiKeyRateLimitService {

    private static final int MAX_REQUESTS_PER_MIN = 25;

    private static final Duration WINDOW_SIZE = Duration.ofMinutes(1);

    private final ConcurrentHashMap<UUID, RateLimiteWindow> rateLimits = new ConcurrentHashMap<>();

    @Override
    public void validateAgainstLimit(UUID apiKeyId) {
        Instant now = Instant.now();

        RateLimiteWindow updatedWindow = rateLimits.compute(apiKeyId, (key, existingWindow) -> {
            if (existingWindow == null || now.isAfter(existingWindow.windowStart().plus(WINDOW_SIZE))) {
                return new RateLimiteWindow(now, 1);
            }

            return new RateLimiteWindow(existingWindow.windowStart(), existingWindow.requestCount() + 1);
        });

        if (updatedWindow.requestCount() > MAX_REQUESTS_PER_MIN) {
            log.error("Rate limit exceeded for apiKeyId: {}", apiKeyId);
            throw new RateLimitExceededException("Rate limit exceeded");
        }
    }
}
