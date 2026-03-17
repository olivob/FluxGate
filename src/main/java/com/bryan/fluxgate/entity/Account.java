package com.bryan.fluxgate.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.bryan.fluxgate.model.enums.AccountStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Table(name = "accounts")
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    private UUID id;

    private String name;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
