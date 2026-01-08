package com.dookie.notification.domain;

import com.dookie.notification.api.dto.NotificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Notification entity.
 * Tests state transitions and business logic.
 */
class NotificationTest {

    @Nested
    @DisplayName("fromRequest factory method")
    class FromRequestTests {
        
        @Test
        @DisplayName("Should create notification with all fields from request")
        void shouldCreateFromRequest() {
            NotificationRequest request = new NotificationRequest(
                    "test-vendor",
                    "https://api.example.com/webhook",
                    "POST",
                    Map.of("Content-Type", "application/json"),
                    "{\"event\": \"test\"}",
                    "idempotency-123"
            );
            
            Notification notification = Notification.fromRequest(request);
            
            assertEquals("test-vendor", notification.getVendorName());
            assertEquals("https://api.example.com/webhook", notification.getTargetUrl());
            assertEquals("POST", notification.getHttpMethod());
            assertEquals(Map.of("Content-Type", "application/json"), notification.getHeaders());
            assertEquals("{\"event\": \"test\"}", notification.getBody());
            assertEquals("idempotency-123", notification.getIdempotencyKey());
            assertEquals(NotificationStatus.PENDING, notification.getStatus());
            assertEquals(0, notification.getRetryCount());
            assertNotNull(notification.getCreatedAt());
            assertNotNull(notification.getUpdatedAt());
            assertTrue(notification.getAttempts().isEmpty());
        }
        
        @Test
        @DisplayName("Should handle null headers")
        void shouldHandleNullHeaders() {
            NotificationRequest request = new NotificationRequest(
                    "test-vendor",
                    "https://api.example.com/webhook",
                    "POST",
                    null,
                    null,
                    null
            );
            
            Notification notification = Notification.fromRequest(request);
            
            assertNotNull(notification.getHeaders());
            assertTrue(notification.getHeaders().isEmpty());
        }
    }

    @Nested
    @DisplayName("Status transitions")
    class StatusTransitionTests {
        
        @Test
        @DisplayName("markDelivered should set status to DELIVERED")
        void markDeliveredShouldSetStatus() {
            Notification notification = createPendingNotification();
            Instant beforeUpdate = notification.getUpdatedAt();
            
            notification.markDelivered();
            
            assertEquals(NotificationStatus.DELIVERED, notification.getStatus());
            assertNull(notification.getNextRetryAt());
            assertTrue(notification.getUpdatedAt().isAfter(beforeUpdate) || 
                       notification.getUpdatedAt().equals(beforeUpdate));
        }
        
        @Test
        @DisplayName("markFailed should set status to FAILED")
        void markFailedShouldSetStatus() {
            Notification notification = createPendingNotification();
            
            notification.markFailed();
            
            assertEquals(NotificationStatus.FAILED, notification.getStatus());
            assertNull(notification.getNextRetryAt());
        }
        
        @Test
        @DisplayName("markCancelled should set status to CANCELLED")
        void markCancelledShouldSetStatus() {
            Notification notification = createPendingNotification();
            
            notification.markCancelled();
            
            assertEquals(NotificationStatus.CANCELLED, notification.getStatus());
            assertNull(notification.getNextRetryAt());
        }
    }

    @Nested
    @DisplayName("Retry handling")
    class RetryTests {
        
        @Test
        @DisplayName("scheduleRetry should increment retry count and set nextRetryAt")
        void scheduleRetryShouldIncrementCount() {
            Notification notification = createPendingNotification();
            assertEquals(0, notification.getRetryCount());
            
            Instant nextRetry = Instant.now().plusSeconds(60);
            notification.scheduleRetry(nextRetry);
            
            assertEquals(1, notification.getRetryCount());
            assertEquals(nextRetry, notification.getNextRetryAt());
            
            // Schedule another retry
            Instant nextRetry2 = Instant.now().plusSeconds(120);
            notification.scheduleRetry(nextRetry2);
            
            assertEquals(2, notification.getRetryCount());
            assertEquals(nextRetry2, notification.getNextRetryAt());
        }
        
        @Test
        @DisplayName("resetForRetry should reset count and set status to PENDING")
        void resetForRetryShouldResetState() {
            Notification notification = createPendingNotification();
            notification.scheduleRetry(Instant.now().plusSeconds(60));
            notification.scheduleRetry(Instant.now().plusSeconds(120));
            notification.markFailed();
            
            assertEquals(NotificationStatus.FAILED, notification.getStatus());
            assertEquals(2, notification.getRetryCount());
            
            notification.resetForRetry();
            
            assertEquals(NotificationStatus.PENDING, notification.getStatus());
            assertEquals(0, notification.getRetryCount());
        }
    }

    @Nested
    @DisplayName("Delivery attempts")
    class DeliveryAttemptTests {
        
        @Test
        @DisplayName("addAttempt should add to attempts list")
        void addAttemptShouldAddToList() {
            Notification notification = createPendingNotification();
            assertTrue(notification.getAttempts().isEmpty());
            
            DeliveryAttempt attempt1 = DeliveryAttempt.success(200, "OK");
            notification.addAttempt(attempt1);
            
            assertEquals(1, notification.getAttempts().size());
            assertEquals(attempt1, notification.getAttempts().get(0));
            
            DeliveryAttempt attempt2 = DeliveryAttempt.failure(500, "Error", "Server error");
            notification.addAttempt(attempt2);
            
            assertEquals(2, notification.getAttempts().size());
            assertEquals(attempt2, notification.getAttempts().get(1));
        }
    }
    
    private Notification createPendingNotification() {
        NotificationRequest request = new NotificationRequest(
                "test-vendor",
                "https://api.example.com/webhook",
                "POST",
                Map.of(),
                "{}",
                null
        );
        return Notification.fromRequest(request);
    }
}
