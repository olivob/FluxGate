package com.bryan.fluxgate.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bryan.fluxgate.entity.ApiKey;
import com.bryan.fluxgate.model.enums.ApiKeyStatus;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, ApiKeyStatus status);
}
