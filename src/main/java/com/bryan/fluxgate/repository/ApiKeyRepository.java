package com.bryan.fluxgate.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bryan.fluxgate.entity.ApiKey;
import com.bryan.fluxgate.model.enums.ApiKeyStatus;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHashAndStatus(@Param("keyHash") String keyHash, @Param("status") ApiKeyStatus status);

    @Query("""
                SELECT ak
                FROM ApiKey ak
                JOIN FETCH ak.account
                WHERE ak.keyHash = :keyHash
                  AND ak.status = :status
            """)
    Optional<ApiKey> findByKeyHashAndStatusWithAccount(@Param("keyHash") String keyHash,
            @Param("status") ApiKeyStatus status);
}
