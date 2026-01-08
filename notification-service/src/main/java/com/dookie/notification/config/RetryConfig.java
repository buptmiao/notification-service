package com.dookie.notification.config;

import com.dookie.notification.retry.RetryDelayCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for retry mechanism components.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4
 */
@Configuration
public class RetryConfig {
    
    /**
     * Creates a RetryDelayCalculator bean configured with properties from application.yml.
     * 
     * @param properties The notification properties
     * @return Configured RetryDelayCalculator
     */
    @Bean
    public RetryDelayCalculator retryDelayCalculator(NotificationProperties properties) {
        return new RetryDelayCalculator(
                properties.getInitialRetryDelay(),
                properties.getMaxRetryDelay()
        );
    }
}
