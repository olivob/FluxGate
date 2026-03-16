package com.bryan.fluxgate.model.response;

import com.bryan.fluxgate.model.dto.ApiKeyDTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateApiKeyResponse {

    private String apiKey;

    private ApiKeyDTO keyMetadata;

}
