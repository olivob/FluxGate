package com.bryan.fluxgate.model.dto;

import java.util.UUID;

public record ChatResponse(
        UUID requestId,
        String provider,
        String model,
        String output) {
}
