package com.bryan.fluxgate.model.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public record RateLimitErrorResponse(
        String error,
        String message,
        int limit,
        int remaining,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant resetAt,
        long retryAfterSeconds) {
}
