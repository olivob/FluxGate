package com.bryan.fluxgate.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;

import com.bryan.fluxgate.provider.OpenAiProviderClient;
import com.bryan.fluxgate.security.ApiRequestLogContext;
import com.fasterxml.jackson.databind.ObjectMapper;

class OpenAiConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(OpenAiConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ApiRequestLogContext.class, () -> new ApiRequestLogContext(new MockHttpServletRequest()));

    @Test
    void disabledProviderNeedsNoCredentialsAndCreatesNoClient() {
        context.run(result -> assertThat(result).hasNotFailed().doesNotHaveBean(OpenAiProviderClient.class));
    }

    @Test
    void enablingWithoutCredentialsFailsStartup() {
        context.withPropertyValues("fluxgate.openai.enabled=true")
                .run(result -> assertThat(result).hasFailed());
    }

    @Test
    void enabledProviderWithCredentialsCreatesClient() {
        context.withPropertyValues("fluxgate.openai.enabled=true", "fluxgate.openai.api-key=test-key",
                "fluxgate.openai.model=test-model")
                .run(result -> assertThat(result).hasNotFailed().hasSingleBean(OpenAiProviderClient.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fluxgate.openai.request-timeout=0ms", "fluxgate.openai.connect-timeout=31s",
            "fluxgate.openai.max-output-tokens=0"})
    void invalidLimitsFailStartup(String property) {
        context.withPropertyValues(property).run(result -> assertThat(result).hasFailed());
    }
}
