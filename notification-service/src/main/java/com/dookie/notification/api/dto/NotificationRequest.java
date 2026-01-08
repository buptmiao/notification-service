package com.dookie.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

/**
 * Represents a notification request from a business system.
 * 
 * @param vendorName     The name of the external vendor (used for metrics and routing)
 * @param targetUrl      The target URL to send the notification to
 * @param httpMethod     The HTTP method to use (GET, POST, PUT, DELETE)
 * @param headers        Optional HTTP headers to include in the request
 * @param body           Optional request body
 * @param idempotencyKey Optional idempotency key provided by the business system
 */
public record NotificationRequest(
    @NotBlank(message = "vendorName is required")
    String vendorName,
    
    @NotBlank(message = "targetUrl is required")
    @Pattern(regexp = "^https?://.*", message = "targetUrl must be a valid HTTP(S) URL")
    String targetUrl,
    
    @NotBlank(message = "httpMethod is required")
    @Pattern(regexp = "^(GET|POST|PUT|DELETE|PATCH)$", message = "httpMethod must be GET, POST, PUT, DELETE, or PATCH")
    String httpMethod,
    
    Map<String, String> headers,
    
    String body,
    
    String idempotencyKey
) {
    /**
     * Creates a NotificationRequest with default empty headers if null.
     */
    public NotificationRequest {
        if (headers == null) {
            headers = Map.of();
        }
    }
}
