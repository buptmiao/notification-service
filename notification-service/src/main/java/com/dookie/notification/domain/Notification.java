package com.dookie.notification.domain;

import com.dookie.notification.api.dto.NotificationRequest;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Notification entity persisted to MongoDB.
 * Represents a notification request and its delivery state.
 */
@Document(collection = "notifications")
@CompoundIndexes({
    @CompoundIndex(name = "status_vendor_idx", def = "{'status': 1, 'vendorName': 1}"),
    @CompoundIndex(name = "status_nextRetryAt_idx", def = "{'status': 1, 'nextRetryAt': 1}")
})
public class Notification {
    
    @Id
    private String id;
    
    private String vendorName;
    private String targetUrl;
    private String httpMethod;
    private Map<String, String> headers;
    private String body;
    
    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;
    
    private NotificationStatus status;
    private int retryCount;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant nextRetryAt;
    
    private List<DeliveryAttempt> attempts;
    
    public Notification() {
        this.attempts = new ArrayList<>();
    }
    
    /**
     * Creates a new Notification from a NotificationRequest.
     */
    public static Notification fromRequest(NotificationRequest request) {
        Notification notification = new Notification();
        notification.vendorName = request.vendorName();
        notification.targetUrl = request.targetUrl();
        notification.httpMethod = request.httpMethod();
        notification.headers = request.headers();
        notification.body = request.body();
        notification.idempotencyKey = request.idempotencyKey();
        notification.status = NotificationStatus.PENDING;
        notification.retryCount = 0;
        notification.createdAt = Instant.now();
        notification.updatedAt = Instant.now();
        notification.attempts = new ArrayList<>();
        return notification;
    }

    /**
     * Adds a delivery attempt to the history.
     */
    public void addAttempt(DeliveryAttempt attempt) {
        if (this.attempts == null) {
            this.attempts = new ArrayList<>();
        }
        this.attempts.add(attempt);
        this.updatedAt = Instant.now();
    }
    
    /**
     * Marks the notification as delivered.
     */
    public void markDelivered() {
        this.status = NotificationStatus.DELIVERED;
        this.updatedAt = Instant.now();
        this.nextRetryAt = null;
    }
    
    /**
     * Marks the notification as failed.
     */
    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.updatedAt = Instant.now();
        this.nextRetryAt = null;
    }
    
    /**
     * Marks the notification as cancelled.
     */
    public void markCancelled() {
        this.status = NotificationStatus.CANCELLED;
        this.updatedAt = Instant.now();
        this.nextRetryAt = null;
    }
    
    /**
     * Schedules a retry at the specified time.
     */
    public void scheduleRetry(Instant nextRetryAt) {
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Resets retry count for manual retry.
     */
    public void resetForRetry() {
        this.retryCount = 0;
        this.status = NotificationStatus.PENDING;
        this.updatedAt = Instant.now();
    }
    
    // Getters
    public String getId() { return id; }
    public String getVendorName() { return vendorName; }
    public String getTargetUrl() { return targetUrl; }
    public String getHttpMethod() { return httpMethod; }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public NotificationStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public List<DeliveryAttempt> getAttempts() { return attempts; }
    
    // Setters
    public void setId(String id) { this.id = id; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    public void setBody(String body) { this.body = body; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public void setAttempts(List<DeliveryAttempt> attempts) { this.attempts = attempts; }
}
