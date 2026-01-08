package com.dookie.notification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Prometheus metrics for the notification service.
 * Provides metrics with vendor_name dimension for monitoring delivery performance.
 * 
 * Requirements: 5.1, 5.2, 5.3
 */
@Component
public class NotificationMetrics {
    
    private static final String METRIC_PREFIX = "notifications";
    private static final String TAG_VENDOR = "vendor_name";
    private static final String TAG_STATUS = "status";
    
    private final MeterRegistry meterRegistry;
    
    // Cache for counters and timers by vendor
    private final Map<String, Counter> totalCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> retryCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> deliveryTimers = new ConcurrentHashMap<>();

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Increments the total notifications counter for a vendor and status.
     * 
     * @param vendorName The vendor name
     * @param status The notification status (received, delivered, failed, pending)
     */
    public void incrementTotal(String vendorName, String status) {
        String key = vendorName + ":" + status;
        totalCounters.computeIfAbsent(key, k -> 
            Counter.builder(METRIC_PREFIX + "_total")
                .description("Total number of notifications")
                .tag(TAG_VENDOR, vendorName)
                .tag(TAG_STATUS, status)
                .register(meterRegistry)
        ).increment();
    }
    
    /**
     * Records the delivery duration for a notification.
     * 
     * @param vendorName The vendor name
     * @param duration The delivery duration
     */
    public void recordDeliveryDuration(String vendorName, Duration duration) {
        deliveryTimers.computeIfAbsent(vendorName, k ->
            Timer.builder(METRIC_PREFIX + "_delivery_duration_seconds")
                .description("Notification delivery duration in seconds")
                .tag(TAG_VENDOR, vendorName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
        ).record(duration);
    }
    
    /**
     * Increments the retry counter for a vendor.
     * 
     * @param vendorName The vendor name
     */
    public void incrementRetry(String vendorName) {
        retryCounters.computeIfAbsent(vendorName, k ->
            Counter.builder(METRIC_PREFIX + "_retry_total")
                .description("Total number of notification retries")
                .tag(TAG_VENDOR, vendorName)
                .register(meterRegistry)
        ).increment();
    }

    /**
     * Records a notification received event.
     * 
     * @param vendorName The vendor name
     */
    public void recordReceived(String vendorName) {
        incrementTotal(vendorName, "received");
    }
    
    /**
     * Records a notification delivered event.
     * 
     * @param vendorName The vendor name
     */
    public void recordDelivered(String vendorName) {
        incrementTotal(vendorName, "delivered");
    }
    
    /**
     * Records a notification failed event.
     * 
     * @param vendorName The vendor name
     */
    public void recordFailed(String vendorName) {
        incrementTotal(vendorName, "failed");
    }
    
    /**
     * Records a notification pending event.
     * 
     * @param vendorName The vendor name
     */
    public void recordPending(String vendorName) {
        incrementTotal(vendorName, "pending");
    }
}
