package com.dookie.notification.messaging;

import java.io.Serializable;

/**
 * Message payload for notification queue.
 * Contains the notification ID to be processed by the delivery worker.
 * 
 * Requirements: 1.4, 7.1
 */
public class NotificationMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String notificationId;
    private int retryCount;
    
    public NotificationMessage() {
    }
    
    public NotificationMessage(String notificationId) {
        this.notificationId = notificationId;
        this.retryCount = 0;
    }
    
    public NotificationMessage(String notificationId, int retryCount) {
        this.notificationId = notificationId;
        this.retryCount = retryCount;
    }
    
    public String getNotificationId() {
        return notificationId;
    }
    
    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
    
    @Override
    public String toString() {
        return "NotificationMessage{" +
                "notificationId='" + notificationId + '\'' +
                ", retryCount=" + retryCount +
                '}';
    }
}
