package com.bryan.fluxgate.service;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bryan.fluxgate.entity.ApiKey;
import com.bryan.fluxgate.model.enums.AccountStatus;
import com.bryan.fluxgate.model.enums.ApiKeyStatus;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.repository.ApiKeyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApiKeyAuthService {

    private final ApiKeyRepository apiKeyRepository;

    private final ApiKeyHashingService apiKeyHashingService;

    private final Clock clock;

    @Transactional
    public ApiKeyPrincipal verifyApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new BadCredentialsException("API key must not be null or empty");
        }
        String hashedKey = apiKeyHashingService.hash(rawApiKey);

        ApiKey apiKeyResponse = apiKeyRepository
                .findByKeyHashAndStatusWithAccount(hashedKey, ApiKeyStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Invalid API key"));

        OffsetDateTime now = OffsetDateTime.now(clock);
        validateApiKey(apiKeyResponse, now);

        // Persist only authentication metadata, never a stale copy of key status.
        apiKeyRepository.recordSuccessfulAuthentication(apiKeyResponse.getId(), now);

        return new ApiKeyPrincipal(apiKeyResponse.getAccountId(), apiKeyResponse.getId());
    }

    private void validateApiKey(ApiKey apiKey, OffsetDateTime now) {
        if (apiKey.getExpiresAt() != null && !apiKey.getExpiresAt().isAfter(now)) {
            throw new BadCredentialsException("API key has expired");
        }

        if (apiKey.getRevokedAt() != null) {
            throw new BadCredentialsException("API key has been revoked");
        }

        if (apiKey.getAccount().getStatus() != AccountStatus.ACTIVE) {
            throw new BadCredentialsException("Account is not active");
        }
    }

}
