package com.dookie.notification.domain;

/**
 * Represents the result of a delivery attempt to an external vendor API.
 * 
 * @param success       Whether the delivery was successful (2xx response)
 * @param statusCode    The HTTP status code received (0 if connection failed)
 * @param responseBody  The response body received
 * @param errorMessage  Error message if the delivery failed
 */
public record DeliveryResult(
    boolean success,
    int statusCode,
    String responseBody,
    String errorMessage
) {
    /**
     * Creates a successful delivery result.
     */
    public static DeliveryResult success(int statusCode, String responseBody) {
        return new DeliveryResult(true, statusCode, responseBody, null);
    }
    
    /**
     * Creates a failed delivery result.
     */
    public static DeliveryResult failure(int statusCode, String responseBody, String errorMessage) {
        return new DeliveryResult(false, statusCode, responseBody, errorMessage);
    }
    
    /**
     * Creates a connection failure result.
     */
    public static DeliveryResult connectionFailure(String errorMessage) {
        return new DeliveryResult(false, 0, null, errorMessage);
    }
    
    /**
     * Checks if the result indicates a retryable error.
     * Default logic: 5xx errors and 429 (Too Many Requests) are retryable.
     */
    public boolean isRetryable() {
        return statusCode >= 500 || statusCode == 429 || statusCode == 0;
    }
}
