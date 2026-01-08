package com.dookie.notification.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RetryDelayCalculator.
 * Tests exponential backoff calculation with jitter.
 * 
 * Requirements: 3.1, 3.2, 3.3, 3.4
 */
class RetryDelayCalculatorTest {

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {
        
        @Test
        @DisplayName("Should create calculator with valid parameters")
        void shouldCreateWithValidParameters() {
            RetryDelayCalculator calculator = new RetryDelayCalculator(
                    Duration.ofSeconds(1),
                    Duration.ofHours(1)
            );
            
            assertEquals(Duration.ofSeconds(1), calculator.getInitialDelay());
            assertEquals(Duration.ofHours(1), calculator.getMaxDelay());
        }
        
        @Test
        @DisplayName("Should throw exception for null initialDelay")
        void shouldThrowForNullInitialDelay() {
            assertThrows(IllegalArgumentException.class, () ->
                    new RetryDelayCalculator(null, Duration.ofHours(1))
            );
        }
        
        @Test
        @DisplayName("Should throw exception for zero initialDelay")
        void shouldThrowForZeroInitialDelay() {
            assertThrows(IllegalArgumentException.class, () ->
                    new RetryDelayCalculator(Duration.ZERO, Duration.ofHours(1))
            );
        }
        
        @Test
        @DisplayName("Should throw exception when maxDelay < initialDelay")
        void shouldThrowWhenMaxDelayLessThanInitialDelay() {
            assertThrows(IllegalArgumentException.class, () ->
                    new RetryDelayCalculator(Duration.ofSeconds(10), Duration.ofSeconds(5))
            );
        }
    }

    @Nested
    @DisplayName("Base delay calculation (without jitter)")
    class BaseDelayTests {
        
        private final RetryDelayCalculator calculator = new RetryDelayCalculator(
                Duration.ofSeconds(1),
                Duration.ofHours(1)
        );
        
        @Test
        @DisplayName("Retry 0: delay = 1s (1 * 2^0)")
        void shouldCalculateDelayForRetry0() {
            Duration delay = calculator.calculateBaseDelay(0);
            assertEquals(Duration.ofSeconds(1), delay);
        }
        
        @Test
        @DisplayName("Retry 1: delay = 2s (1 * 2^1)")
        void shouldCalculateDelayForRetry1() {
            Duration delay = calculator.calculateBaseDelay(1);
            assertEquals(Duration.ofSeconds(2), delay);
        }
        
        @Test
        @DisplayName("Retry 2: delay = 4s (1 * 2^2)")
        void shouldCalculateDelayForRetry2() {
            Duration delay = calculator.calculateBaseDelay(2);
            assertEquals(Duration.ofSeconds(4), delay);
        }
        
        @Test
        @DisplayName("Retry 5: delay = 32s (1 * 2^5)")
        void shouldCalculateDelayForRetry5() {
            Duration delay = calculator.calculateBaseDelay(5);
            assertEquals(Duration.ofSeconds(32), delay);
        }
        
        @Test
        @DisplayName("Should cap at maxDelay when exponential exceeds it")
        void shouldCapAtMaxDelay() {
            // 2^20 = 1,048,576 seconds > 1 hour (3600 seconds)
            Duration delay = calculator.calculateBaseDelay(20);
            assertEquals(Duration.ofHours(1), delay);
        }
        
        @Test
        @DisplayName("Should throw for negative retry count")
        void shouldThrowForNegativeRetryCount() {
            assertThrows(IllegalArgumentException.class, () ->
                    calculator.calculateBaseDelay(-1)
            );
        }
    }

    @Nested
    @DisplayName("Jitter application")
    class JitterTests {
        
        @Test
        @DisplayName("Jitter should be within ±20% of base delay")
        void jitterShouldBeWithinBounds() {
            Duration baseDelay = Duration.ofSeconds(10);
            long baseMillis = baseDelay.toMillis();
            long minExpected = (long) (baseMillis * 0.8);  // -20%
            long maxExpected = (long) (baseMillis * 1.2);  // +20%
            
            // Test with multiple random seeds
            for (int seed = 0; seed < 100; seed++) {
                RetryDelayCalculator calculator = new RetryDelayCalculator(
                        Duration.ofSeconds(1),
                        Duration.ofHours(1),
                        new Random(seed)
                );
                
                Duration delayWithJitter = calculator.applyJitter(baseDelay);
                long actualMillis = delayWithJitter.toMillis();
                
                assertTrue(actualMillis >= minExpected && actualMillis <= maxExpected,
                        String.format("Delay %dms should be between %dms and %dms (seed=%d)",
                                actualMillis, minExpected, maxExpected, seed));
            }
        }
        
        @Test
        @DisplayName("Delay with jitter should never be zero or negative")
        void delayWithJitterShouldBePositive() {
            Duration smallDelay = Duration.ofMillis(1);
            
            for (int seed = 0; seed < 100; seed++) {
                RetryDelayCalculator calculator = new RetryDelayCalculator(
                        Duration.ofMillis(1),
                        Duration.ofHours(1),
                        new Random(seed)
                );
                
                Duration delayWithJitter = calculator.applyJitter(smallDelay);
                assertTrue(delayWithJitter.toMillis() >= 1,
                        "Delay should be at least 1ms");
            }
        }
    }

    @Nested
    @DisplayName("Full delay calculation (with jitter)")
    class FullDelayTests {
        
        @Test
        @DisplayName("calculateDelay should return value within jitter bounds")
        void calculateDelayShouldIncludeJitter() {
            // Use fixed seed for deterministic test
            RetryDelayCalculator calculator = new RetryDelayCalculator(
                    Duration.ofSeconds(1),
                    Duration.ofHours(1),
                    new Random(42)
            );
            
            // For retry 2, base delay = 4s
            // With ±20% jitter, expected range: 3.2s - 4.8s
            Duration delay = calculator.calculateDelay(2);
            long delayMillis = delay.toMillis();
            
            assertTrue(delayMillis >= 3200 && delayMillis <= 4800,
                    String.format("Delay %dms should be between 3200ms and 4800ms", delayMillis));
        }
    }
}
