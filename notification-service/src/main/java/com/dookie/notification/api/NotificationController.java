package com.dookie.notification.api;

import com.dookie.notification.api.dto.ErrorResponse;
import com.dookie.notification.api.dto.NotificationResponse;
import com.dookie.notification.api.dto.NotificationStatusResponse;
import com.dookie.notification.domain.Notification;
import com.dookie.notification.api.dto.NotificationRequest;
import com.dookie.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for notification operations.
 * Provides endpoints for creating and querying notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    
    private final NotificationService notificationService;
    
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    
    /**
     * Creates a new notification request.
     * Returns 202 Accepted with the notification ID.
     * 
     * Requirements: 1.1, 1.2, 1.3, 5.4
     */
    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody NotificationRequest request) {
        
        Notification notification = notificationService.createNotification(request);
        
        NotificationResponse response = new NotificationResponse(
            notification.getId(),
            notification.getStatus(),
            notification.getCreatedAt()
        );
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    /**
     * Retrieves notification status by ID.
     * Returns 404 if notification not found.
     * 
     * Requirements: 4.1, 4.2, 4.3
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getNotificationStatus(@PathVariable String id) {
        var optionalNotification = notificationService.findById(id);
        
        if (optionalNotification.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse(
                404,
                "Not Found",
                "Notification not found with id: " + id
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        
        Notification notification = optionalNotification.get();
        NotificationStatusResponse response = new NotificationStatusResponse(
            notification.getId(),
            notification.getVendorName(),
            notification.getTargetUrl(),
            notification.getHttpMethod(),
            notification.getStatus(),
            notification.getRetryCount(),
            notification.getCreatedAt(),
            notification.getUpdatedAt(),
            notification.getNextRetryAt(),
            notification.getAttempts()
        );
        return ResponseEntity.ok(response);
    }
    
    /**
     * Manually retries a failed notification from the Dead Letter Queue.
     * Resets the retry count and re-queues the notification for processing.
     * 
     * Requirements: 6.1
     * 
     * @param id The notification ID
     * @return 200 OK with updated notification, 404 if not found, 409 if not in FAILED status
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryNotification(@PathVariable String id) {
        var optionalNotification = notificationService.findById(id);
        
        if (optionalNotification.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse(
                404,
                "Not Found",
                "Notification not found with id: " + id
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        
        var result = notificationService.resetForRetry(id);
        
        if (result.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse(
                409,
                "Conflict",
                "Notification is not in FAILED status and cannot be retried"
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }
        
        Notification notification = result.get();
        NotificationResponse response = new NotificationResponse(
            notification.getId(),
            notification.getStatus(),
            notification.getCreatedAt()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Cancels a pending notification.
     * Marks the notification as CANCELLED and removes it from the processing queue.
     * 
     * Requirements: 6.2
     * 
     * @param id The notification ID
     * @return 204 No Content on success, 404 if not found, 409 if not in PENDING status
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelNotification(@PathVariable String id) {
        var optionalNotification = notificationService.findById(id);
        
        if (optionalNotification.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse(
                404,
                "Not Found",
                "Notification not found with id: " + id
            );
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        }
        
        var result = notificationService.cancelNotification(id);
        
        if (result.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse(
                409,
                "Conflict",
                "Notification is not in PENDING status and cannot be cancelled"
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
        }
        
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Lists failed notifications for operator review.
     * Optionally filters by vendor name.
     * 
     * This endpoint allows operators to discover which notifications have failed
     * and need manual intervention (retry or investigation).
     * 
     * @param vendorName Optional vendor name filter
     * @return List of failed notifications
     */
    @GetMapping("/failed")
    public ResponseEntity<List<NotificationStatusResponse>> listFailedNotifications(
            @RequestParam(required = false) String vendorName) {
        
        List<Notification> failedNotifications;
        if (vendorName != null && !vendorName.isBlank()) {
            failedNotifications = notificationService.findByVendorAndStatus(
                    vendorName, 
                    com.dookie.notification.domain.NotificationStatus.FAILED
            );
        } else {
            failedNotifications = notificationService.findByStatus(
                    com.dookie.notification.domain.NotificationStatus.FAILED
            );
        }
        
        List<NotificationStatusResponse> responses = failedNotifications.stream()
                .map(notification -> new NotificationStatusResponse(
                        notification.getId(),
                        notification.getVendorName(),
                        notification.getTargetUrl(),
                        notification.getHttpMethod(),
                        notification.getStatus(),
                        notification.getRetryCount(),
                        notification.getCreatedAt(),
                        notification.getUpdatedAt(),
                        notification.getNextRetryAt(),
                        notification.getAttempts()
                ))
                .toList();
        
        return ResponseEntity.ok(responses);
    }
}
