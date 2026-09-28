package com.bryan.fluxgate.security;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import com.bryan.fluxgate.model.RequestAttributeKeys;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequestScope
@RequiredArgsConstructor
public class ApiRequestLogContext {

    private final HttpServletRequest request;

    public UUID getRequestId() {
        return Objects.requireNonNull((UUID) request.getAttribute(RequestAttributeKeys.REQUEST_ID),
                "Request ID must be initialized by ApiRequestLogFilter");
    }

    public void setProvider(String provider) {
        request.setAttribute(RequestAttributeKeys.PROVIDER, provider);
    }

    public void setModel(String model) {
        request.setAttribute(RequestAttributeKeys.MODEL, model);
    }

    public void setErrorCode(String errorCode) {
        request.setAttribute(RequestAttributeKeys.ERROR_CODE, errorCode);
    }
}
