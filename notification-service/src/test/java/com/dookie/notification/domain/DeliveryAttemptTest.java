package com.dookie.notification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeliveryAttempt record.
 */
class DeliveryAttemptTest {

    @Test
    @DisplayName("success() should create attempt with no error message")
    void successShouldCreateAttemptWithNoError() {
        DeliveryAttempt attempt = DeliveryAttempt.success(200, "OK");
        
        assertEquals(200, attempt.responseCode());
        assertEquals("OK", attempt.responseBody());
        assertNull(attempt.errorMessage());
        assertNotNull(attempt.timestamp());
    }
    
    @Test
    @DisplayName("failure() should create attempt with error message")
    void failureShouldCreateAttemptWithError() {
        DeliveryAttempt attempt = DeliveryAttempt.failure(500, "Internal Server Error", "Server error");
        
        assertEquals(500, attempt.responseCode());
        assertEquals("Internal Server Error", attempt.responseBody());
        assertEquals("Server error", attempt.errorMessage());
        assertNotNull(attempt.timestamp());
    }
    
    @Test
    @DisplayName("connectionFailure() should create attempt with status 0")
    void connectionFailureShouldCreateAttemptWithZeroStatus() {
        DeliveryAttempt attempt = DeliveryAttempt.connectionFailure("Connection refused");
        
        assertEquals(0, attempt.responseCode());
        assertNull(attempt.responseBody());
        assertEquals("Connection refused", attempt.errorMessage());
        assertNotNull(attempt.timestamp());
    }
}
