package com.bryan.fluxgate.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.bryan.fluxgate.model.enums.ApiKeyStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiKeyDTO {

    private UUID id;

    private String name;

    private String keyPrefix;

    private ApiKeyStatus status;

    private OffsetDateTime createdAt;

    private OffsetDateTime lastUsedAt;

    private OffsetDateTime expiresAt;
}
