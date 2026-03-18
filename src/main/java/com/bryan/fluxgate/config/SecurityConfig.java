package com.bryan.fluxgate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.bryan.fluxgate.repository.ApiRequestLogRepository;
import com.bryan.fluxgate.security.ApiKeyAuthenticationFilter;
import com.bryan.fluxgate.security.ApiRequestLogFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public AuthenticationManager getAuthenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public ApiKeyAuthenticationFilter getApiKeyAuthenticationFilter(AuthenticationManager authenticationManager) {
        return new ApiKeyAuthenticationFilter(authenticationManager);
    }

    @Bean
    public ApiRequestLogFilter getApiRequestLogFilter(ApiRequestLogRepository apiRequestLogRepository) {
        return new ApiRequestLogFilter(apiRequestLogRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter, ApiRequestLogFilter apiRequestLogFilter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/v1/verifyKey").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiRequestLogFilter, ApiKeyAuthenticationFilter.class)
                .build();
    }
}