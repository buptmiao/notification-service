package com.dookie.notification.domain;

import java.time.Instant;

/**
 * Records a single delivery attempt to an external vendor API.
 * 
 * @param timestamp     When the delivery attempt was made
 * @param responseCode  The HTTP response code received (0 if connection failed)
 * @param responseBody  The response body received (may be truncated for large responses)
 * @param errorMessage  Error message if the attempt failed
 */
public record DeliveryAttempt(
    Instant timestamp,
    int responseCode,
    String responseBody,
    String errorMessage
) {
    /**
     * Creates a successful delivery attempt.
     */
    public static DeliveryAttempt success(int responseCode, String responseBody) {
        return new DeliveryAttempt(Instant.now(), responseCode, responseBody, null);
    }
    
    /**
     * Creates a failed delivery attempt.
     */
    public static DeliveryAttempt failure(int responseCode, String responseBody, String errorMessage) {
        return new DeliveryAttempt(Instant.now(), responseCode, responseBody, errorMessage);
    }
    
    /**
     * Creates a connection failure attempt.
     */
    public static DeliveryAttempt connectionFailure(String errorMessage) {
        return new DeliveryAttempt(Instant.now(), 0, null, errorMessage);
    }
}
