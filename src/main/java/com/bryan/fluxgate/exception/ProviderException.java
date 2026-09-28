package com.bryan.fluxgate.exception;

import lombok.Getter;

@Getter
public class ProviderException extends RuntimeException {
    public enum Kind {
        NOT_CONFIGURED, TIMEOUT, UNAVAILABLE, RATE_LIMITED, AUTHENTICATION_FAILED,
        REJECTED_REQUEST, INVALID_RESPONSE, INCOMPLETE_RESPONSE, REFUSAL
    }

    private final Kind kind;

    public ProviderException(Kind kind) {
        // Never retain a raw upstream response, prompt, or authorization header in the exception.
        super("Provider failure: " + kind);
        this.kind = kind;
    }
}
