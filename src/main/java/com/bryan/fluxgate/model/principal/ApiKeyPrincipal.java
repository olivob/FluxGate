package com.bryan.fluxgate.model.principal;

import java.util.UUID;

public record ApiKeyPrincipal(
        UUID accountId,
        UUID apiKeyId) {
}
