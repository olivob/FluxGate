package com.bryan.fluxgate.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.model.response.ApiKeyVerificationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/v1")
public class ApiKeyController {

    @GetMapping("/verifyKey")
    public ResponseEntity<ApiKeyVerificationResponse> getAccount(Authentication authentication) {
        log.info("Verifying API key");
        ApiKeyPrincipal apiKeyPrincipal = (ApiKeyPrincipal) authentication.getPrincipal();

        ApiKeyVerificationResponse response = ApiKeyVerificationResponse.builder()
                .accountId(apiKeyPrincipal.accountId()).apiKeyId(apiKeyPrincipal.apiKeyId()).build();

        return ResponseEntity.ok().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception e) {
        log.error("Generic exception encountered while parsing Api Key: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(BadCredentialsException e) {
        log.error("API key authentication failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

}
