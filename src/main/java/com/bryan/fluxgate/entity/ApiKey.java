package com.bryan.fluxgate.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.bryan.fluxgate.model.enums.ApiKeyStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name = "api_keys")
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    private UUID id;

    @Column(name = "account_id")
    private UUID accountId;

    // because of LAZY, any time i am doing apiKey.getAccount() i will need to
    // annotate with @Transactional
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;

    @Column(name = "key_hash")
    private String keyHash;

    @Column(name = "key_prefix")
    private String keyPrefix;

    private String name;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ApiKeyStatus status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

}
