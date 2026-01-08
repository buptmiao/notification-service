package com.dookie.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration properties for the notification service.
 * 
 * Requirements: 3.1, 3.2, 3.3
 */
@Configuration
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {
    
    /**
     * Maximum number of retry attempts before moving to DLQ.
     * Default: 5
     */
    private int maxRetryCount = 5;
    
    /**
     * Initial delay before first retry.
     * Default: 1 second
     */
    private Duration initialRetryDelay = Duration.ofSeconds(1);
    
    /**
     * Maximum delay between retries.
     * Default: 1 hour
     */
    private Duration maxRetryDelay = Duration.ofHours(1);
    
    /**
     * HTTP timeout for external API calls.
     * Default: 30 seconds
     */
    private Duration httpTimeout = Duration.ofSeconds(30);
    
    public int getMaxRetryCount() {
        return maxRetryCount;
    }
    
    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }
    
    public Duration getInitialRetryDelay() {
        return initialRetryDelay;
    }
    
    public void setInitialRetryDelay(Duration initialRetryDelay) {
        this.initialRetryDelay = initialRetryDelay;
    }
    
    public Duration getMaxRetryDelay() {
        return maxRetryDelay;
    }
    
    public void setMaxRetryDelay(Duration maxRetryDelay) {
        this.maxRetryDelay = maxRetryDelay;
    }
    
    public Duration getHttpTimeout() {
        return httpTimeout;
    }
    
    public void setHttpTimeout(Duration httpTimeout) {
        this.httpTimeout = httpTimeout;
    }
}
