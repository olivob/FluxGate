package com.bryan.fluxgate.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank @Size(max = 100) String model,

        @NotBlank @Size(max = 10000) String prompt) {
}
