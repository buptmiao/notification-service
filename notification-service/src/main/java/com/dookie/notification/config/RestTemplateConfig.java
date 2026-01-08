package com.dookie.notification.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for RestTemplate used by vendor adapters.
 * Uses NotificationProperties for centralized configuration.
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4
 */
@Configuration
public class RestTemplateConfig {
    
    /**
     * Default connect timeout (5 seconds).
     * Used when httpTimeout is not specified or for connect timeout specifically.
     */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    
    /**
     * Creates a RestTemplate with configured timeouts.
     * Uses httpTimeout from NotificationProperties for read timeout.
     * Used by vendor adapters to make HTTP requests to external APIs.
     * 
     * @param builder The RestTemplateBuilder
     * @param properties The notification properties containing httpTimeout
     * @return Configured RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, NotificationProperties properties) {
        Duration httpTimeout = properties.getHttpTimeout();
        return builder
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .readTimeout(httpTimeout)
                .build();
    }
}
