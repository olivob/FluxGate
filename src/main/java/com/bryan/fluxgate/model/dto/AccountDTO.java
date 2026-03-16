package com.bryan.fluxgate.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountDTO {

    private UUID id;

    private String name;

    private String status;

    private OffsetDateTime createdAt;

}
