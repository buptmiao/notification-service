package com.dookie.notification.api.dto;

import com.dookie.notification.domain.NotificationStatus;
import java.time.Instant;

/**
 * Response DTO for notification creation.
 * Returns the notification ID and initial status.
 */
public record NotificationResponse(
    String id,
    NotificationStatus status,
    Instant createdAt
) {}
