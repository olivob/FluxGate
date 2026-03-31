package com.bryan.fluxgate.security;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bryan.fluxgate.entity.ApiRequestLog;
import com.bryan.fluxgate.model.RequestAttributeKeys;
import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.repository.ApiRequestLogRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ApiRequestLogFilter extends OncePerRequestFilter {

    private final ApiRequestLogRepository apiLogRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startMs = System.currentTimeMillis();
        OffsetDateTime requestedTime = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long endMs = System.currentTimeMillis();
            long latencyMs = endMs - startMs;

            OffsetDateTime completedTime = OffsetDateTime.now(ZoneOffset.UTC);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            String provider = (String) request.getAttribute(RequestAttributeKeys.PROVIDER);
            String model = (String) request.getAttribute(RequestAttributeKeys.MODEL);
            String errorCode = (String) request.getAttribute(RequestAttributeKeys.ERROR_CODE);

            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof ApiKeyPrincipal principal) {

                ApiRequestLog apiRequestLog = ApiRequestLog.builder().id(UUID.randomUUID())
                        .accountId(principal.accountId()).apiKeyId(principal.apiKeyId()).path(request.getRequestURI())
                        .method(request.getMethod()).statusCode(response.getStatus()).requestedAt(requestedTime)
                        .completedAt(completedTime).latencyMs((int) latencyMs).provider(provider).model(model)
                        .errorCode(errorCode).build();

                try {
                    apiLogRepository.save(apiRequestLog);
                } catch (Exception e) {
                    log.error("Failed to save API request log because of: {}", e.getMessage(), e);
                }
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
