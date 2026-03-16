package com.bryan.fluxgate.service;

import java.time.OffsetDateTime;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import com.bryan.fluxgate.entity.Account;
import com.bryan.fluxgate.entity.ApiKey;
import com.bryan.fluxgate.model.dto.AccountDTO;
import com.bryan.fluxgate.model.enums.ApiKeyStatus;
import com.bryan.fluxgate.model.response.ApiKeyVerificationResponse;
import com.bryan.fluxgate.repository.ApiKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyVerificationResponse verifyApiKey(String apiKey) {
        String hashedKey = DigestUtils.sha256Hex(apiKey);

        ApiKey apiKeyResponse = apiKeyRepository
                .findByKeyHashAndStatus(hashedKey, ApiKeyStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Invalid API key"));

        validateApiKey(apiKeyResponse);

        return ApiKeyVerificationResponse.builder().accountId(apiKeyResponse.getAccountId())
                .apiKeyId(apiKeyResponse.getId()).build();
    }

    private void validateApiKey(ApiKey apiKey) {
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Api key has expired!");
        }

        if (apiKey.getRevokedAt() != null) {
            throw new RuntimeException("Api key has been revoked!");
        }
    }

    private AccountDTO mapAccountToDTO(Account account) {
        return AccountDTO.builder()
                .id(account.getId())
                .name(account.getName())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
