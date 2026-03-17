package com.bryan.fluxgate.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.bryan.fluxgate.model.enums.AccountStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountDTO {

    private UUID id;

    private String name;

    private AccountStatus status;

    private OffsetDateTime createdAt;

}
