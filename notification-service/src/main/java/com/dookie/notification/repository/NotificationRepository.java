package com.dookie.notification.repository;

import com.dookie.notification.domain.Notification;
import com.dookie.notification.domain.NotificationStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB repository for Notification entities.
 * Provides CRUD operations and custom queries for notification management.
 * 
 * Indexes are defined on the Notification entity:
 * - status + vendorName: for query optimization on status filtering by vendor
 * - status + nextRetryAt: for finding notifications due for retry
 * 
 * Requirements: 1.4, 4.1
 */
@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    
    /**
     * Find notifications by status.
     * Used for querying pending, failed, or delivered notifications.
     */
    List<Notification> findByStatus(NotificationStatus status);
    
    /**
     * Find notifications by vendor name and status.
     * Used for vendor-specific monitoring and operations.
     */
    List<Notification> findByVendorNameAndStatus(String vendorName, NotificationStatus status);
    
    /**
     * Find notifications that are due for retry.
     * Returns notifications with PENDING status and nextRetryAt before the given time.
     * Used by the delivery worker to pick up notifications for retry.
     */
    List<Notification> findByStatusAndNextRetryAtBefore(NotificationStatus status, Instant time);
    
    /**
     * Find notification by idempotency key.
     * Used to prevent duplicate notification creation.
     */
    Optional<Notification> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Count notifications by status.
     * Used for monitoring metrics.
     */
    long countByStatus(NotificationStatus status);
    
    /**
     * Count notifications by vendor name and status.
     * Used for vendor-specific monitoring metrics.
     */
    long countByVendorNameAndStatus(String vendorName, NotificationStatus status);
}
