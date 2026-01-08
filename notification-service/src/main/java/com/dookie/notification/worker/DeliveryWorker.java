package com.dookie.notification.worker;

import com.dookie.notification.adapter.VendorAdapter;
import com.dookie.notification.adapter.VendorAdapterRegistry;
import com.dookie.notification.config.NotificationProperties;
import com.dookie.notification.config.RabbitMQConfig;
import com.dookie.notification.domain.DeliveryAttempt;
import com.dookie.notification.domain.DeliveryResult;
import com.dookie.notification.domain.Notification;
import com.dookie.notification.domain.NotificationStatus;
import com.dookie.notification.messaging.NotificationMessage;
import com.dookie.notification.messaging.NotificationProducer;
import com.dookie.notification.metrics.NotificationMetrics;
import com.dookie.notification.retry.RetryDelayCalculator;
import com.dookie.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Worker that consumes notifications from RabbitMQ and delivers them to external APIs.
 * Uses VendorAdapterRegistry to route to the appropriate adapter based on vendor name.
 * Implements retry scheduling with exponential backoff for retryable errors.
 * 
 * Requirements: 2.1, 2.2, 2.4, 2.5, 7.1
 */
@Component
public class DeliveryWorker {
    
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);
    
    private final NotificationService notificationService;
    private final VendorAdapterRegistry vendorAdapterRegistry;
    private final NotificationProducer notificationProducer;
    private final RetryDelayCalculator retryDelayCalculator;
    private final NotificationProperties notificationProperties;
    private final NotificationMetrics notificationMetrics;
    
    public DeliveryWorker(NotificationService notificationService,
                          VendorAdapterRegistry vendorAdapterRegistry,
                          NotificationProducer notificationProducer,
                          RetryDelayCalculator retryDelayCalculator,
                          NotificationProperties notificationProperties,
                          NotificationMetrics notificationMetrics) {
        this.notificationService = notificationService;
        this.vendorAdapterRegistry = vendorAdapterRegistry;
        this.notificationProducer = notificationProducer;
        this.retryDelayCalculator = retryDelayCalculator;
        this.notificationProperties = notificationProperties;
        this.notificationMetrics = notificationMetrics;
    }
    
    /**
     * Processes notification messages from the queue.
     * Fetches the notification, delivers it using the appropriate adapter,
     * and updates the status based on the result.
     * 
     * Requirements: 2.1, 7.1
     * 
     * @param message The notification message containing the notification ID
     */
    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void processNotification(NotificationMessage message) {
        String notificationId = message.getNotificationId();
        log.info("Processing notification: {}", notificationId);
        
        // Fetch the notification from the database
        Optional<Notification> optionalNotification = notificationService.findById(notificationId);
        
        if (optionalNotification.isEmpty()) {
            log.warn("Notification {} not found, skipping", notificationId);
            return;
        }
        
        Notification notification = optionalNotification.get();
        String vendorName = notification.getVendorName();
        
        // Skip if already delivered, failed, or cancelled
        if (notification.getStatus() != NotificationStatus.PENDING) {
            log.info("Notification {} is not pending (status: {}), skipping", 
                    notificationId, notification.getStatus());
            return;
        }
        
        // Get the appropriate adapter for this vendor
        VendorAdapter adapter = vendorAdapterRegistry.getAdapter(vendorName);
        
        if (adapter == null) {
            log.error("No adapter found for vendor: {}, marking notification {} as failed", 
                    vendorName, notificationId);
            DeliveryAttempt attempt = DeliveryAttempt.failure(0, null, 
                    "No adapter found for vendor: " + vendorName);
            notificationService.markFailed(notificationId, attempt);
            notificationMetrics.recordFailed(vendorName);
            return;
        }
        
        log.debug("Using adapter {} for notification {}", 
                adapter.getVendorName(), notificationId);
        
        // Record delivery start time for duration metrics
        Instant deliveryStart = Instant.now();
        
        // Attempt delivery
        DeliveryResult result = adapter.deliver(notification);
        
        // Record delivery duration
        Duration deliveryDuration = Duration.between(deliveryStart, Instant.now());
        notificationMetrics.recordDeliveryDuration(vendorName, deliveryDuration);
        
        // Update notification status based on result
        handleDeliveryResult(notification, adapter, result);
    }
    
    /**
     * Handles the delivery result and updates notification status accordingly.
     * Implements retry scheduling with exponential backoff for retryable errors.
     * Moves to DLQ when max retries are exceeded.
     * 
     * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
     * 
     * @param notification The notification that was delivered
     * @param adapter The adapter used for delivery
     * @param result The delivery result
     */
    private void handleDeliveryResult(Notification notification, VendorAdapter adapter, DeliveryResult result) {
        String notificationId = notification.getId();
        String vendorName = notification.getVendorName();
        int currentRetryCount = notification.getRetryCount();
        int maxRetries = notificationProperties.getMaxRetryCount();
        
        if (result.success()) {
            // Successful delivery - mark as delivered
            DeliveryAttempt attempt = DeliveryAttempt.success(
                    result.statusCode(), 
                    truncateResponseBody(result.responseBody())
            );
            notificationService.markDelivered(notificationId, attempt);
            notificationMetrics.recordDelivered(vendorName);
            log.info("Notification {} delivered successfully", notificationId);
            
        } else if (adapter.isRetryable(result.statusCode(), result.responseBody())) {
            // Retryable error - schedule retry or move to DLQ
            DeliveryAttempt attempt = DeliveryAttempt.failure(
                    result.statusCode(),
                    truncateResponseBody(result.responseBody()),
                    result.errorMessage()
            );
            
            if (currentRetryCount >= maxRetries) {
                // Max retries exceeded - mark as failed (MongoDB is source of truth)
                log.warn("Notification {} exceeded max retries ({}), marking as FAILED", 
                        notificationId, maxRetries);
                notificationService.markFailed(notificationId, attempt);
                notificationMetrics.recordFailed(vendorName);
            } else {
                // Schedule retry with exponential backoff
                scheduleRetry(notification, attempt);
                notificationMetrics.incrementRetry(vendorName);
            }
            
        } else {
            // Non-retryable error - mark as failed
            DeliveryAttempt attempt = DeliveryAttempt.failure(
                    result.statusCode(),
                    truncateResponseBody(result.responseBody()),
                    result.errorMessage()
            );
            notificationService.markFailed(notificationId, attempt);
            notificationMetrics.recordFailed(vendorName);
            log.warn("Notification {} delivery failed with non-retryable error (status: {})", 
                    notificationId, result.statusCode());
        }
    }
    
    /**
     * Schedules a retry for a notification with exponential backoff.
     * Calculates delay based on retry count and publishes with delay.
     * 
     * Requirements: 2.2, 2.4, 3.4
     * 
     * @param notification The notification to retry
     * @param attempt The failed delivery attempt
     */
    private void scheduleRetry(Notification notification, DeliveryAttempt attempt) {
        String notificationId = notification.getId();
        int currentRetryCount = notification.getRetryCount();
        int nextRetryCount = currentRetryCount + 1;
        
        // Calculate delay using exponential backoff with jitter
        Duration delay = retryDelayCalculator.calculateDelay(currentRetryCount);
        Instant nextRetryAt = Instant.now().plus(delay);
        
        // Update notification with retry info
        notificationService.scheduleRetry(notificationId, attempt, nextRetryAt);
        
        // Publish with delay for scheduled retry
        notificationProducer.publishWithDelay(notificationId, nextRetryCount, delay.toMillis());
        
        log.info("Notification {} scheduled for retry #{} in {}ms (at {})", 
                notificationId, nextRetryCount, delay.toMillis(), nextRetryAt);
    }
    
    /**
     * Truncates response body to prevent storing excessively large responses.
     * 
     * @param responseBody The original response body
     * @return Truncated response body (max 1000 characters)
     */
    private String truncateResponseBody(String responseBody) {
        if (responseBody == null) {
            return null;
        }
        int maxLength = 1000;
        if (responseBody.length() <= maxLength) {
            return responseBody;
        }
        return responseBody.substring(0, maxLength) + "... [truncated]";
    }
}
