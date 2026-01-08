package com.dookie.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeliveryResult record.
 */
class DeliveryResultTest {

    @Test
    @DisplayName("success() should create successful result")
    void successShouldCreateSuccessfulResult() {
        DeliveryResult result = DeliveryResult.success(200, "OK");
        
        assertTrue(result.success());
        assertEquals(200, result.statusCode());
        assertEquals("OK", result.responseBody());
        assertNull(result.errorMessage());
    }
    
    @Test
    @DisplayName("failure() should create failed result")
    void failureShouldCreateFailedResult() {
        DeliveryResult result = DeliveryResult.failure(500, "Error", "Server error");
        
        assertFalse(result.success());
        assertEquals(500, result.statusCode());
        assertEquals("Error", result.responseBody());
        assertEquals("Server error", result.errorMessage());
    }
    
    @Test
    @DisplayName("connectionFailure() should create result with status 0")
    void connectionFailureShouldCreateResultWithZeroStatus() {
        DeliveryResult result = DeliveryResult.connectionFailure("Connection refused");
        
        assertFalse(result.success());
        assertEquals(0, result.statusCode());
        assertNull(result.responseBody());
        assertEquals("Connection refused", result.errorMessage());
    }
    
    @Test
    @DisplayName("isRetryable() should return true for 5xx errors")
    void isRetryableShouldReturnTrueFor5xx() {
        assertTrue(DeliveryResult.failure(500, null, null).isRetryable());
        assertTrue(DeliveryResult.failure(502, null, null).isRetryable());
        assertTrue(DeliveryResult.failure(503, null, null).isRetryable());
    }
    
    @Test
    @DisplayName("isRetryable() should return true for 429")
    void isRetryableShouldReturnTrueFor429() {
        assertTrue(DeliveryResult.failure(429, null, null).isRetryable());
    }
    
    @Test
    @DisplayName("isRetryable() should return true for connection failures")
    void isRetryableShouldReturnTrueForConnectionFailures() {
        assertTrue(DeliveryResult.connectionFailure("Connection refused").isRetryable());
    }
    
    @Test
    @DisplayName("isRetryable() should return false for 4xx errors (except 429)")
    void isRetryableShouldReturnFalseFor4xx() {
        assertFalse(DeliveryResult.failure(400, null, null).isRetryable());
        assertFalse(DeliveryResult.failure(401, null, null).isRetryable());
        assertFalse(DeliveryResult.failure(404, null, null).isRetryable());
    }
}
