package com.dookie.notification.retry;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Random;

/**
 * Calculates retry delays using exponential backoff with jitter.
 * 
 * Formula: delay = min(initialDelay * 2^retryCount, maxDelay) ± 20% jitter
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4
 */
@Component
public class RetryDelayCalculator {
    
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final Random random;
    
    // Jitter percentage (±20%)
    private static final double JITTER_FACTOR = 0.2;
    
    /**
     * Creates a RetryDelayCalculator with default values.
     * Default: initialDelay = 1s, maxDelay = 1h
     */
    public RetryDelayCalculator() {
        this(Duration.ofSeconds(1), Duration.ofHours(1));
    }
    
    /**
     * Creates a RetryDelayCalculator with specified delays.
     * 
     * @param initialDelay The initial retry delay (default: 1 second)
     * @param maxDelay The maximum retry delay (default: 1 hour)
     */
    public RetryDelayCalculator(Duration initialDelay, Duration maxDelay) {
        this(initialDelay, maxDelay, new Random());
    }
    
    /**
     * Creates a RetryDelayCalculator with specified delays and random source.
     * Useful for testing with deterministic random values.
     * 
     * @param initialDelay The initial retry delay
     * @param maxDelay The maximum retry delay
     * @param random The random source for jitter calculation
     */
    public RetryDelayCalculator(Duration initialDelay, Duration maxDelay, Random random) {
        if (initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay must be positive");
        }
        if (maxDelay == null || maxDelay.isNegative() || maxDelay.isZero()) {
            throw new IllegalArgumentException("maxDelay must be positive");
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= initialDelay");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.random = random;
    }
    
    /**
     * Calculates the retry delay for a given retry count.
     * 
     * Uses exponential backoff: delay = min(initialDelay * 2^retryCount, maxDelay)
     * Then applies random jitter of ±20% to prevent thundering herd.
     * 
     * Requirements: 3.4
     * 
     * @param retryCount The current retry count (0-based)
     * @return The calculated delay with jitter applied
     */
    public Duration calculateDelay(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be non-negative");
        }
        
        // Calculate base delay with exponential backoff
        Duration baseDelay = calculateBaseDelay(retryCount);
        
        // Apply jitter (±20%)
        return applyJitter(baseDelay);
    }
    
    /**
     * Calculates the base delay without jitter.
     * Formula: min(initialDelay * 2^retryCount, maxDelay)
     * 
     * @param retryCount The current retry count (0-based)
     * @return The base delay before jitter
     */
    public Duration calculateBaseDelay(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be non-negative");
        }
        
        // Calculate 2^retryCount, capping to prevent overflow
        // For retryCount >= 62, the multiplier would overflow, so we cap it
        long multiplier;
        if (retryCount >= 62) {
            multiplier = Long.MAX_VALUE;
        } else {
            multiplier = 1L << retryCount; // 2^retryCount
        }
        
        // Calculate delay in milliseconds
        long initialMillis = initialDelay.toMillis();
        long maxMillis = maxDelay.toMillis();
        
        // Check for overflow and cap at maxDelay
        long delayMillis;
        if (multiplier > maxMillis / initialMillis) {
            // Would overflow or exceed max, use max
            delayMillis = maxMillis;
        } else {
            delayMillis = Math.min(initialMillis * multiplier, maxMillis);
        }
        
        return Duration.ofMillis(delayMillis);
    }
    
    /**
     * Applies random jitter to a delay.
     * Jitter is ±20% of the base delay.
     * 
     * @param baseDelay The base delay to apply jitter to
     * @return The delay with jitter applied
     */
    public Duration applyJitter(Duration baseDelay) {
        if (baseDelay == null || baseDelay.isNegative()) {
            throw new IllegalArgumentException("baseDelay must be non-negative");
        }
        
        long baseMillis = baseDelay.toMillis();
        
        // Calculate jitter range: ±20% of base delay
        // jitterRange = baseDelay * JITTER_FACTOR
        long jitterRange = (long) (baseMillis * JITTER_FACTOR);
        
        // Generate random jitter in range [-jitterRange, +jitterRange]
        // random.nextDouble() returns [0, 1), so (random.nextDouble() * 2 - 1) gives [-1, 1)
        double jitterMultiplier = random.nextDouble() * 2 - 1; // Range: [-1, 1)
        long jitter = (long) (jitterRange * jitterMultiplier);
        
        // Apply jitter, ensuring result is at least 1ms
        long delayWithJitter = Math.max(1, baseMillis + jitter);
        
        return Duration.ofMillis(delayWithJitter);
    }
    
    /**
     * Gets the initial delay configuration.
     * 
     * @return The initial delay
     */
    public Duration getInitialDelay() {
        return initialDelay;
    }
    
    /**
     * Gets the maximum delay configuration.
     * 
     * @return The maximum delay
     */
    public Duration getMaxDelay() {
        return maxDelay;
    }
    
    /**
     * Gets the jitter factor (0.2 = 20%).
     * 
     * @return The jitter factor
     */
    public static double getJitterFactor() {
        return JITTER_FACTOR;
    }
}
