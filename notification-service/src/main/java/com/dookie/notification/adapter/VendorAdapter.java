package com.dookie.notification.adapter;

import com.dookie.notification.domain.DeliveryResult;
import com.dookie.notification.domain.Notification;

/**
 * Interface for external vendor API adapters.
 * Different vendors can have different implementations to handle
 * specific request formats, authentication methods, etc.
 */
public interface VendorAdapter {
    
    /**
     * Gets the vendor name this adapter supports.
     * 
     * @return the vendor name
     */
    String getVendorName();
    
    /**
     * Delivers a notification to the external vendor API.
     * 
     * @param notification the notification to deliver
     * @return the delivery result
     */
    DeliveryResult deliver(Notification notification);
    
    /**
     * Determines if a response indicates a retryable error.
     * Different vendors may have different retry strategies.
     * 
     * @param statusCode   the HTTP status code received
     * @param responseBody the response body received
     * @return true if the error is retryable, false otherwise
     */
    boolean isRetryable(int statusCode, String responseBody);
}
