package com.bryan.fluxgate.security;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

        UUID requestId = UUID.randomUUID();
        request.setAttribute(RequestAttributeKeys.REQUEST_ID, requestId);
        response.setHeader("X-Request-ID", requestId.toString());
        long startNanos = System.nanoTime();
        OffsetDateTime requestedTime = OffsetDateTime.now(ZoneOffset.UTC);
        boolean failed = false;

        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            // The container may not have set a 500 status yet while unwinding filters.
            failed = true;
            request.setAttribute(RequestAttributeKeys.ERROR_CODE, "internal_error");
            log.error("Unhandled request failure requestId={}", requestId, e);
            throw e;
        } finally {
            long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

            OffsetDateTime completedTime = OffsetDateTime.now(ZoneOffset.UTC);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            String provider = (String) request.getAttribute(RequestAttributeKeys.PROVIDER);
            String model = (String) request.getAttribute(RequestAttributeKeys.MODEL);
            String errorCode = (String) request.getAttribute(RequestAttributeKeys.ERROR_CODE);
            int statusCode = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
            if (errorCode == null && statusCode >= 400) {
                errorCode = "http_" + statusCode;
            }

            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof ApiKeyPrincipal principal) {

                ApiRequestLog apiRequestLog = ApiRequestLog.builder().id(requestId)
                        .accountId(principal.accountId()).apiKeyId(principal.apiKeyId()).path(request.getRequestURI())
                        .method(request.getMethod()).statusCode(statusCode).requestedAt(requestedTime)
                        .completedAt(completedTime).latencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE))
                        .provider(provider).model(model)
                        .errorCode(errorCode).build();

                try {
                    apiLogRepository.save(apiRequestLog);
                } catch (Exception e) {
                    log.error("Failed to save API request log requestId={}", requestId, e);
                }
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !(path.equals("/v1") || path.startsWith("/v1/"));
    }
}
