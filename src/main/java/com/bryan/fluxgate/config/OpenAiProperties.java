package com.bryan.fluxgate.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties("fluxgate.openai")
public class OpenAiProperties {
    private boolean enabled;
    private String apiKey = "";
    private String model = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(30);
    @Min(1)
    @Max(16384)
    private int maxOutputTokens = 1024;

    @AssertTrue(message = "OpenAI requires an API key and model when enabled")
    public boolean isCredentialsConfigured() {
        return !enabled || (apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank());
    }

    @AssertTrue(message = "OpenAI timeouts must be between 1 ms and 5 minutes; connect timeout must not exceed request timeout")
    public boolean isTimeoutConfigurationValid() {
        return validTimeout(connectTimeout) && validTimeout(requestTimeout)
                && connectTimeout.compareTo(requestTimeout) <= 0;
    }

    private boolean validTimeout(Duration timeout) {
        return timeout != null && timeout.compareTo(Duration.ofMillis(1)) >= 0
                && timeout.compareTo(Duration.ofMinutes(5)) <= 0;
    }
}
