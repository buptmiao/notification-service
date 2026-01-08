package com.dookie.notification.service;

import com.dookie.notification.domain.DeliveryAttempt;
import com.dookie.notification.domain.Notification;
import com.dookie.notification.api.dto.NotificationRequest;
import com.dookie.notification.domain.NotificationStatus;
import com.dookie.notification.messaging.NotificationProducer;
import com.dookie.notification.metrics.NotificationMetrics;
import com.dookie.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for notification business logic.
 * Handles creation, persistence, and status management.
 * 
 * Requirements: 1.4, 7.2
 */
@Service
public class NotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    
    private final NotificationRepository notificationRepository;
    private final NotificationProducer notificationProducer;
    private final NotificationMetrics notificationMetrics;
    
    public NotificationService(NotificationRepository notificationRepository,
                               NotificationProducer notificationProducer,
                               NotificationMetrics notificationMetrics) {
        this.notificationRepository = notificationRepository;
        this.notificationProducer = notificationProducer;
        this.notificationMetrics = notificationMetrics;
    }
    
    /**
     * Creates a new notification from a request.
     * Persists to durable storage before returning (at-least-once semantics).
     * Publishes to queue after persistence for processing.
     * 
     * Requirements: 1.4, 7.1, 7.2
     * 
     * @param request The notification request
     * @return The persisted notification with generated ID
     */
    public Notification createNotification(NotificationRequest request) {
        // Check for duplicate by idempotency key if provided
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<Notification> existing = notificationRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Returning existing notification for idempotency key: {}", request.idempotencyKey());
                return existing.get();
            }
        }
        
        // Create notification with PENDING status
        Notification notification = Notification.fromRequest(request);
        
        // Persist before returning acknowledgment (ensures durability)
        Notification saved = notificationRepository.save(notification);
        log.info("Created notification with id: {} for vendor: {}", saved.getId(), saved.getVendorName());
        
        // Record metrics for received notification
        notificationMetrics.recordReceived(saved.getVendorName());
        notificationMetrics.recordPending(saved.getVendorName());
        
        // Publish to queue for processing (after persistence)
        notificationProducer.publishNotification(saved.getId());
        
        return saved;
    }
    
    /**
     * Finds a notification by ID.
     * 
     * Requirements: 4.1
     * 
     * @param id The notification ID
     * @return Optional containing the notification if found
     */
    public Optional<Notification> findById(String id) {
        return notificationRepository.findById(id);
    }
    
    /**
     * Updates a notification after a successful delivery.
     * 
     * Requirements: 2.1
     * 
     * @param id The notification ID
     * @param attempt The delivery attempt record
     * @return The updated notification
     */
    public Optional<Notification> markDelivered(String id, DeliveryAttempt attempt) {
        return notificationRepository.findById(id)
            .map(notification -> {
                notification.addAttempt(attempt);
                notification.markDelivered();
                Notification saved = notificationRepository.save(notification);
                log.info("Notification {} marked as DELIVERED", id);
                return saved;
            });
    }
    
    /**
     * Updates a notification after a failed delivery (non-retryable).
     * 
     * Requirements: 2.3
     * 
     * @param id The notification ID
     * @param attempt The delivery attempt record
     * @return The updated notification
     */
    public Optional<Notification> markFailed(String id, DeliveryAttempt attempt) {
        return notificationRepository.findById(id)
            .map(notification -> {
                notification.addAttempt(attempt);
                notification.markFailed();
                Notification saved = notificationRepository.save(notification);
                log.info("Notification {} marked as FAILED", id);
                return saved;
            });
    }
    
    /**
     * Schedules a notification for retry.
     * 
     * Requirements: 2.2, 2.4
     * 
     * @param id The notification ID
     * @param attempt The delivery attempt record
     * @param nextRetryAt When to retry
     * @return The updated notification
     */
    public Optional<Notification> scheduleRetry(String id, DeliveryAttempt attempt, Instant nextRetryAt) {
        return notificationRepository.findById(id)
            .map(notification -> {
                notification.addAttempt(attempt);
                notification.scheduleRetry(nextRetryAt);
                Notification saved = notificationRepository.save(notification);
                log.info("Notification {} scheduled for retry at {}, attempt #{}", 
                    id, nextRetryAt, notification.getRetryCount());
                return saved;
            });
    }
    
    /**
     * Cancels a pending notification.
     * 
     * Requirements: 6.2
     * 
     * @param id The notification ID
     * @return The updated notification if found and was pending
     */
    public Optional<Notification> cancelNotification(String id) {
        return notificationRepository.findById(id)
            .filter(n -> n.getStatus() == NotificationStatus.PENDING)
            .map(notification -> {
                notification.markCancelled();
                Notification saved = notificationRepository.save(notification);
                log.info("Notification {} cancelled", id);
                return saved;
            });
    }
    
    /**
     * Resets a failed notification for manual retry.
     * Re-queues the notification for processing.
     * 
     * Requirements: 6.1
     * 
     * @param id The notification ID
     * @return The updated notification if found and was failed
     */
    public Optional<Notification> resetForRetry(String id) {
        return notificationRepository.findById(id)
            .filter(n -> n.getStatus() == NotificationStatus.FAILED)
            .map(notification -> {
                String vendorName = notification.getVendorName();
                notification.resetForRetry();
                Notification saved = notificationRepository.save(notification);
                log.info("Notification {} reset for retry", id);
                
                // Update metrics - record pending
                notificationMetrics.recordPending(vendorName);
                
                // Re-queue for processing
                notificationProducer.publishNotification(saved.getId());
                
                return saved;
            });
    }
    
    /**
     * Finds notifications by status.
     * 
     * @param status The notification status
     * @return List of notifications with the given status
     */
    public List<Notification> findByStatus(NotificationStatus status) {
        return notificationRepository.findByStatus(status);
    }
    
    /**
     * Finds notifications by vendor and status.
     * 
     * @param vendorName The vendor name
     * @param status The notification status
     * @return List of notifications matching the criteria
     */
    public List<Notification> findByVendorAndStatus(String vendorName, NotificationStatus status) {
        return notificationRepository.findByVendorNameAndStatus(vendorName, status);
    }
    
    /**
     * Finds notifications due for retry.
     * 
     * @return List of pending notifications with nextRetryAt in the past
     */
    public List<Notification> findDueForRetry() {
        return notificationRepository.findByStatusAndNextRetryAtBefore(
            NotificationStatus.PENDING, 
            Instant.now()
        );
    }
    
    /**
     * Counts notifications by status.
     * Used for monitoring metrics.
     * 
     * @param status The notification status
     * @return Count of notifications with the given status
     */
    public long countByStatus(NotificationStatus status) {
        return notificationRepository.countByStatus(status);
    }
    
    /**
     * Counts notifications by vendor and status.
     * Used for vendor-specific monitoring metrics.
     * 
     * @param vendorName The vendor name
     * @param status The notification status
     * @return Count of notifications matching the criteria
     */
    public long countByVendorAndStatus(String vendorName, NotificationStatus status) {
        return notificationRepository.countByVendorNameAndStatus(vendorName, status);
    }
}
