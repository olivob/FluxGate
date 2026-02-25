package com.bryan.fluxgate.service;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import com.bryan.fluxgate.repository.ApiKeyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiAuthKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public void verifyApiKey(String apiKey) {
        String hashedKey = DigestUtils.sha256Hex(apiKey);

        // check hashed key against the database
    }
}
