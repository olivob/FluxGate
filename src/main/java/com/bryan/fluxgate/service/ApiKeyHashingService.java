package com.bryan.fluxgate.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyHashingService {

    public String hash(String rawKey) {
        return DigestUtils.sha256Hex(rawKey);
    }
}
