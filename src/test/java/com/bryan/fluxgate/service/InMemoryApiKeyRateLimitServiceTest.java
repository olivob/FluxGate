package com.bryan.fluxgate.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bryan.fluxgate.exception.RateLimitExceededException;
import com.bryan.fluxgate.model.RateLimitCheckResponse;

class InMemoryApiKeyRateLimitServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private final Clock clock = mock(Clock.class);
    private final UUID key = UUID.randomUUID();
    private final InMemoryApiKeyRateLimitService limiter = new InMemoryApiKeyRateLimitService(clock);

    @BeforeEach
    void setTime() {
        when(clock.instant()).thenReturn(START);
    }

    @Test
    void admitsFiveRequestsAndRejectsSubsequentRequests() {
        for (int request = 1; request <= 5; request++) {
            RateLimitCheckResponse result = limiter.validateAgainstLimit(key);
            assertTrue(result.allowed());
            assertEquals(5, result.limit());
            assertEquals(5 - request, result.remaining());
            assertEquals(START.plusSeconds(60), result.resetAt());
            assertEquals(0, result.retryAfterSeconds());
        }

        for (int request = 0; request < 3; request++) {
            RateLimitExceededException exception = assertThrows(RateLimitExceededException.class,
                    () -> limiter.validateAgainstLimit(key));
            assertEquals(5, exception.getLimit());
            assertEquals(0, exception.getRemaining());
            assertEquals(START.plusSeconds(60), exception.getResetAt());
            assertEquals(60, exception.getRetryAfterSeconds());
        }
    }

    @Test
    void opensANewWindowAtTheExactExpirationBoundary() {
        exhaustWindow();
        when(clock.instant()).thenReturn(START.plusSeconds(60));

        for (int request = 1; request <= 5; request++) {
            RateLimitCheckResponse result = limiter.validateAgainstLimit(key);
            assertEquals(5 - request, result.remaining());
            assertEquals(START.plusSeconds(120), result.resetAt());
        }
        assertThrows(RateLimitExceededException.class, () -> limiter.validateAgainstLimit(key));
    }

    @Test
    void roundsRetryDelayUpAndDoesNotExtendTheWindowOnRejection() {
        exhaustWindow();
        when(clock.instant()).thenReturn(START.plusSeconds(59).plusNanos(999_999_999));

        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class,
                () -> limiter.validateAgainstLimit(key));
        assertEquals(1, exception.getRetryAfterSeconds());
        assertEquals(START.plusSeconds(60), exception.getResetAt());

        when(clock.instant()).thenReturn(START.plusSeconds(60));
        assertTrue(limiter.validateAgainstLimit(key).allowed());
    }

    @Test
    void startsTheNextWindowAtTheFirstRequestAfterAnIdlePeriod() {
        exhaustWindow();
        when(clock.instant()).thenReturn(START.plusSeconds(180));

        RateLimitCheckResponse result = limiter.validateAgainstLimit(key);
        assertEquals(4, result.remaining());
        assertEquals(START.plusSeconds(240), result.resetAt());
    }

    @Test
    void keysHaveIndependentBudgets() {
        exhaustWindow();
        assertEquals(4, limiter.validateAgainstLimit(UUID.randomUUID()).remaining());
        assertThrows(RateLimitExceededException.class, () -> limiter.validateAgainstLimit(key));
    }

    @Test
    void admitsExactlyFiveConcurrentRequestsForOneKey() throws Exception {
        int requests = 32;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(requests)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int request = 0; request < requests; request++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start requests");
                    }
                    try {
                        return limiter.validateAgainstLimit(key).allowed();
                    } catch (RateLimitExceededException expected) {
                        return false;
                    }
                }));
            }

            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS), "Workers did not become ready");
            } finally {
                start.countDown();
            }
            int admitted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    admitted++;
                }
            }
            assertEquals(5, admitted);
        }
    }

    private void exhaustWindow() {
        for (int request = 0; request < 5; request++) {
            limiter.validateAgainstLimit(key);
        }
    }
}
