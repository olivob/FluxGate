package com.bryan.fluxgate.service;

import java.time.OffsetDateTime;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.bryan.fluxgate.entity.ApiKey;
import com.bryan.fluxgate.model.enums.AccountStatus;
import com.bryan.fluxgate.model.enums.ApiKeyStatus;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.repository.ApiKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthService {

    private final ApiKeyRepository apiKeyRepository;

    private final ApiKeyHashingService apiKeyHashingService;

    public ApiKeyPrincipal verifyApiKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new BadCredentialsException("API key must not be null or empty");
        }
        String hashedKey = apiKeyHashingService.hash(rawApiKey);

        ApiKey apiKeyResponse = apiKeyRepository
                .findByKeyHashAndStatusWithAccount(hashedKey, ApiKeyStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Invalid API key"));

        validateApiKey(apiKeyResponse);

        // will optimize writes later

        apiKeyResponse.setLastUsedAt(OffsetDateTime.now());

        return new ApiKeyPrincipal(apiKeyResponse.getAccountId(), apiKeyResponse.getId());
    }

    private void validateApiKey(ApiKey apiKey) {
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadCredentialsException("API key has expired");
        }

        if (apiKey.getRevokedAt() != null) {
            throw new BadCredentialsException("API key has been revoked");
        }

        if (apiKey.getAccount().getStatus() != AccountStatus.ACTIVE) {
            throw new BadCredentialsException("Account is not active");
        }
    }

    // private AccountDTO mapAccountToDTO(Account account) {
    // return AccountDTO.builder()
    // .id(account.getId())
    // .name(account.getName())
    // .status(account.getStatus())
    // .createdAt(account.getCreatedAt())
    // .build();
    // }
}
