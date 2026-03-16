package com.bryan.fluxgate.model.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiKeyVerificationResponse {

    private UUID accountId;
    private UUID apiKeyId;
}