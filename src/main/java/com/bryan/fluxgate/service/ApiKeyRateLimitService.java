package com.bryan.fluxgate.service;

import java.util.UUID;

public interface ApiKeyRateLimitService {
    void validateAgainstLimit(UUID apiKeyId);
}