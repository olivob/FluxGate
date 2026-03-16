package com.bryan.fluxgate.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bryan.fluxgate.model.response.ApiKeyVerificationResponse;
import com.bryan.fluxgate.service.ApiKeyAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ApiKeyController {

    private final ApiKeyAuthService apiAuthKeyService;

    @GetMapping("/verifyKey")
    public ResponseEntity<ApiKeyVerificationResponse> getAccount(@RequestHeader("X-API-Key") String apiKey) {
        ApiKeyVerificationResponse response = apiAuthKeyService.verifyApiKey(apiKey);

        return ResponseEntity.ok().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception e) {
        log.error("Generic exception encountered while parsing Api Key: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(e.getMessage());
    }

}
