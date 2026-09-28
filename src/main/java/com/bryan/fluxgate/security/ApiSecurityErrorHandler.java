package com.bryan.fluxgate.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.bryan.fluxgate.model.response.ApiErrorResponse;
import com.bryan.fluxgate.model.RequestAttributeKeys;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        request.setAttribute(RequestAttributeKeys.ERROR_CODE, "unauthorized");
        write(response, HttpServletResponse.SC_UNAUTHORIZED,
                new ApiErrorResponse("unauthorized", "A valid X-API-Key header is required."));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        request.setAttribute(RequestAttributeKeys.ERROR_CODE, "forbidden");
        write(response, HttpServletResponse.SC_FORBIDDEN,
                new ApiErrorResponse("forbidden", "Access to this resource is not permitted."));
    }

    private void write(HttpServletResponse response, int status, ApiErrorResponse body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
