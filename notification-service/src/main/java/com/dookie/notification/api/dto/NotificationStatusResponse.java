package com.dookie.notification.api.dto;

import com.dookie.notification.domain.DeliveryAttempt;
import com.dookie.notification.domain.NotificationStatus;
import java.time.Instant;
import java.util.List;

/**
 * Response DTO for notification status query.
 * Includes full delivery attempt history.
 */
public record NotificationStatusResponse(
    String id,
    String vendorName,
    String targetUrl,
    String httpMethod,
    NotificationStatus status,
    int retryCount,
    Instant createdAt,
    Instant updatedAt,
    Instant nextRetryAt,
    List<DeliveryAttempt> attempts
) {}
