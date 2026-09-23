package com.bryan.fluxgate.model;

import java.time.Instant;

public record RateLimitCheckResponse(
                boolean allowed,
                int limit,
                int remaining,
                Instant resetAt,
                long retryAfterSeconds) {
}
