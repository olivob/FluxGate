package com.bryan.fluxgate.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.RequestAttributes;

import com.bryan.fluxgate.exception.RateLimitExceededException;
import com.bryan.fluxgate.model.response.RateLimitErrorResponse;
import com.bryan.fluxgate.model.response.ApiErrorResponse;
import com.bryan.fluxgate.model.RequestAttributeKeys;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<RateLimitErrorResponse> handleRateLimitExceeded(RateLimitExceededException e,
            HttpServletRequest request) {
        request.setAttribute(RequestAttributeKeys.ERROR_CODE, "rate_limit_exceeded");
        String message = "Request limit reached. Retry in %d %s.".formatted(
                e.getRetryAfterSeconds(), e.getRetryAfterSeconds() == 1 ? "second" : "seconds");
        RateLimitErrorResponse body = new RateLimitErrorResponse(
                "rate_limit_exceeded", message, e.getLimit(), e.getRemaining(),
                e.getResetAt(), e.getRetryAfterSeconds());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(e.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception e, HttpServletRequest request) {
        request.setAttribute(RequestAttributeKeys.ERROR_CODE, "internal_error");
        log.error("Request failed requestId={}", request.getAttribute(RequestAttributeKeys.REQUEST_ID), e);
        return ResponseEntity.internalServerError().body(
                new ApiErrorResponse("internal_error", "An unexpected error occurred. Contact support with the X-Request-ID."));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        // Preserve Spring MVC's validation/parsing/status handling rather than turning it into a 500.
        request.setAttribute(RequestAttributeKeys.ERROR_CODE, "http_" + statusCode.value(),
                RequestAttributes.SCOPE_REQUEST);
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
