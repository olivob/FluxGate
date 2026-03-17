package com.bryan.fluxgate.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private final String credentials;

    public ApiKeyAuthenticationToken(String rawApiKey) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.principal = null;
        this.credentials = rawApiKey;
        setAuthenticated(false);
    }

    public ApiKeyAuthenticationToken(ApiKeyPrincipal apiKeyPrincipal) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.principal = apiKeyPrincipal;
        this.credentials = null;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return this.credentials;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

}
