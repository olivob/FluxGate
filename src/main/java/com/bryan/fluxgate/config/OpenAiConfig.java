package com.bryan.fluxgate.config;

import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bryan.fluxgate.provider.OpenAiProviderClient;
import com.bryan.fluxgate.security.ApiRequestLogContext;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "fluxgate.openai.enabled", havingValue = "true")
    public HttpClient openAiHttpClient(OpenAiProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "fluxgate.openai.enabled", havingValue = "true")
    public OpenAiProviderClient openAiProviderClient(HttpClient openAiHttpClient, ObjectMapper mapper,
            OpenAiProperties properties, ApiRequestLogContext logContext) {
        return new OpenAiProviderClient(openAiHttpClient, mapper, properties, logContext);
    }
}
