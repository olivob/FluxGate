package com.bryan.fluxgate.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.service.ApiKeyAuthService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyAuthService apiKeyAuthService;

    @Override
    public Authentication authenticate(Authentication authentication) {
        String rawApiKey = (String) authentication.getCredentials();

        ApiKeyPrincipal apiKeyPrincipal = apiKeyAuthService.verifyApiKey(rawApiKey);

        return new ApiKeyAuthenticationToken(apiKeyPrincipal);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }

}
