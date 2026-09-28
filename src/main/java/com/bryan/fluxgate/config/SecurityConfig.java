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
import com.bryan.fluxgate.security.ApiSecurityErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    @Bean
    public AuthenticationManager getAuthenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public ApiSecurityErrorHandler apiSecurityErrorHandler(ObjectMapper objectMapper) {
        return new ApiSecurityErrorHandler(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            AuthenticationManager authenticationManager, ApiRequestLogRepository apiRequestLogRepository,
            ApiSecurityErrorHandler securityErrors)
            throws Exception {
        // Register these only in the security chain, not as separate servlet filters.
        ApiKeyAuthenticationFilter apiKeyAuthenticationFilter =
                new ApiKeyAuthenticationFilter(authenticationManager, securityErrors);
        ApiRequestLogFilter apiRequestLogFilter = new ApiRequestLogFilter(apiRequestLogRepository);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(securityErrors)
                        .accessDeniedHandler(securityErrors))
                .authorizeHttpRequests(auth -> auth
                        // Preserve the original status on servlet error dispatches.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/health", "/actuator/health").permitAll()
                        .requestMatchers("/v1/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiRequestLogFilter, ApiKeyAuthenticationFilter.class)
                .build();
    }
}
