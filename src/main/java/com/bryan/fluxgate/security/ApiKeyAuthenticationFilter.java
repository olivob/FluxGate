package com.bryan.fluxgate.security;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    private final static String API_KEY_HEADER = "X-API-Key";

    private final AuthenticationManager authenticationManager;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/health") || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawApiKey = request.getHeader(API_KEY_HEADER);

        if (StringUtils.isBlank(rawApiKey)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Authentication authRequest = new ApiKeyAuthenticationToken(rawApiKey);
            Authentication authResponse = authenticationManager.authenticate(authRequest);

            SecurityContextHolder.getContext().setAuthentication(authResponse);
        } catch (BadCredentialsException e) {
            SecurityContextHolder.clearContext();
            log.warn("API key authentication failed: {}", e.getMessage());
            authenticationEntryPoint.commence(request, response, e);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
