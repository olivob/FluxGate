package com.bryan.fluxgate.service;

import java.time.OffsetDateTime;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.bryan.fluxgate.entity.Account;
import com.bryan.fluxgate.entity.ApiKey;
import com.bryan.fluxgate.model.dto.AccountDTO;
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

    public ApiKeyPrincipal verifyApiKey(String apiKey) {
        String hashedKey = DigestUtils.sha256Hex(apiKey);

        ApiKey apiKeyResponse = apiKeyRepository
                .findByKeyHashAndStatusWithAccount(hashedKey, ApiKeyStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Invalid API key"));

        validateApiKey(apiKeyResponse);

        return new ApiKeyPrincipal(apiKeyResponse.getAccountId(), apiKeyResponse.getId());
    }

    private void validateApiKey(ApiKey apiKey) {
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadCredentialsException("Api key has expired!");
        }

        if (apiKey.getRevokedAt() != null) {
            throw new BadCredentialsException("Api key has been revoked!");
        }

        if (apiKey.getAccount() == null) {
            throw new BadCredentialsException("Account not found");
        }

        if (apiKey.getAccount().getStatus() != AccountStatus.ACTIVE) {
            throw new BadCredentialsException("Account inactive");
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
