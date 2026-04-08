package com.bryan.fluxgate.model;

import java.time.Instant;

public record RateLimiteWindow(
                Instant windowStart,
                int requestCount) {
}