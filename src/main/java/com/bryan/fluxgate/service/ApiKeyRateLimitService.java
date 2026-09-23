package com.bryan.fluxgate.service;

import java.util.UUID;

import com.bryan.fluxgate.model.RateLimitCheckResponse;

public interface ApiKeyRateLimitService {
    RateLimitCheckResponse validateAgainstLimit(UUID apiKeyId);
}