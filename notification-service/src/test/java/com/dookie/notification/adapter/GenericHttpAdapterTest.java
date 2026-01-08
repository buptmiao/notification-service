package com.dookie.notification.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GenericHttpAdapter.
 * Tests retry logic for different HTTP status codes.
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4
 */
class GenericHttpAdapterTest {

    @Nested
    @DisplayName("isRetryable logic")
    class IsRetryableTests {
        
        // Create adapter with null RestTemplate since we're only testing isRetryable
        private final GenericHttpAdapter adapter = new GenericHttpAdapter(null);
        
        @Test
        @DisplayName("2xx success codes should not be retryable")
        void successCodesShouldNotBeRetryable() {
            assertFalse(adapter.isRetryable(200, null));
            assertFalse(adapter.isRetryable(201, null));
            assertFalse(adapter.isRetryable(204, null));
        }
        
        @Test
        @DisplayName("4xx client errors (except 429) should not be retryable")
        void clientErrorsShouldNotBeRetryable() {
            assertFalse(adapter.isRetryable(400, null)); // Bad Request
            assertFalse(adapter.isRetryable(401, null)); // Unauthorized
            assertFalse(adapter.isRetryable(403, null)); // Forbidden
            assertFalse(adapter.isRetryable(404, null)); // Not Found
            assertFalse(adapter.isRetryable(422, null)); // Unprocessable Entity
        }
        
        @Test
        @DisplayName("429 Too Many Requests should be retryable")
        void tooManyRequestsShouldBeRetryable() {
            assertTrue(adapter.isRetryable(429, null));
        }
        
        @Test
        @DisplayName("5xx server errors should be retryable")
        void serverErrorsShouldBeRetryable() {
            assertTrue(adapter.isRetryable(500, null)); // Internal Server Error
            assertTrue(adapter.isRetryable(502, null)); // Bad Gateway
            assertTrue(adapter.isRetryable(503, null)); // Service Unavailable
            assertTrue(adapter.isRetryable(504, null)); // Gateway Timeout
        }
        
        @Test
        @DisplayName("Connection failures (status 0) should be retryable")
        void connectionFailuresShouldBeRetryable() {
            assertTrue(adapter.isRetryable(0, null));
        }
    }
    
    @Test
    @DisplayName("getVendorName should return 'generic'")
    void shouldReturnGenericVendorName() {
        GenericHttpAdapter adapter = new GenericHttpAdapter(null);
        assertEquals("generic", adapter.getVendorName());
    }
}
