package com.dookie.notification.domain;

/**
 * Represents the status of a notification in the system.
 */
public enum NotificationStatus {
    /**
     * Notification has been accepted and is waiting to be processed.
     */
    PENDING,
    
    /**
     * Notification has been successfully delivered to the external vendor API.
     */
    DELIVERED,
    
    /**
     * Notification delivery has failed after all retry attempts.
     */
    FAILED,
    
    /**
     * Notification has been cancelled by an operator.
     */
    CANCELLED
}
