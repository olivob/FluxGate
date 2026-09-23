package com.bryan.fluxgate.exception;

import java.time.Instant;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final int limit;
    private final int remaining;
    private final Instant resetAt;
    private final long retryAfterSeconds;

    public RateLimitExceededException(
            String message,
            int limit,
            int remaining,
            Instant resetAt,
            long retryAfterSeconds) {
        super(message);
        this.limit = limit;
        this.remaining = remaining;
        this.resetAt = resetAt;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
