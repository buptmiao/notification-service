package com.dookie.notification.adapter;

import com.dookie.notification.domain.DeliveryResult;
import com.dookie.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Generic HTTP adapter for standard RESTful APIs.
 * Directly uses the NotificationRequest's URL, Headers, and Body to send requests.
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4
 */
@Component
public class GenericHttpAdapter implements VendorAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(GenericHttpAdapter.class);
    private static final String VENDOR_NAME = "generic";
    
    private final RestTemplate restTemplate;
    
    public GenericHttpAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public String getVendorName() {
        return VENDOR_NAME;
    }
    
    @Override
    public DeliveryResult deliver(Notification notification) {
        String targetUrl = notification.getTargetUrl();
        String httpMethod = notification.getHttpMethod();
        
        log.debug("Delivering notification {} to {} via {}", 
                notification.getId(), targetUrl, httpMethod);
        
        try {
            HttpHeaders headers = buildHeaders(notification.getHeaders());
            HttpEntity<String> requestEntity = new HttpEntity<>(notification.getBody(), headers);
            HttpMethod method = HttpMethod.valueOf(httpMethod.toUpperCase());
            
            ResponseEntity<String> response = restTemplate.exchange(
                    targetUrl,
                    method,
                    requestEntity,
                    String.class
            );
            
            int statusCode = response.getStatusCode().value();
            String responseBody = response.getBody();
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Notification {} delivered successfully with status {}", 
                        notification.getId(), statusCode);
                return DeliveryResult.success(statusCode, responseBody);
            } else {
                // This branch handles non-2xx responses that don't throw exceptions
                log.warn("Notification {} received non-success status {}", 
                        notification.getId(), statusCode);
                return DeliveryResult.failure(statusCode, responseBody, 
                        "Received non-success status: " + statusCode);
            }
            
        } catch (HttpClientErrorException e) {
            // 4xx errors
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.warn("Notification {} received client error {}: {}", 
                    notification.getId(), statusCode, e.getMessage());
            return DeliveryResult.failure(statusCode, responseBody, e.getMessage());
            
        } catch (HttpServerErrorException e) {
            // 5xx errors
            int statusCode = e.getStatusCode().value();
            String responseBody = e.getResponseBodyAsString();
            log.warn("Notification {} received server error {}: {}", 
                    notification.getId(), statusCode, e.getMessage());
            return DeliveryResult.failure(statusCode, responseBody, e.getMessage());
            
        } catch (ResourceAccessException e) {
            // Connection timeout, connection refused, etc.
            log.error("Notification {} connection failed: {}", 
                    notification.getId(), e.getMessage());
            return DeliveryResult.connectionFailure("Connection failed: " + e.getMessage());
            
        } catch (Exception e) {
            // Other unexpected errors
            log.error("Notification {} delivery failed with unexpected error: {}", 
                    notification.getId(), e.getMessage(), e);
            return DeliveryResult.connectionFailure("Unexpected error: " + e.getMessage());
        }
    }
    
    @Override
    public boolean isRetryable(int statusCode, String responseBody) {
        // 5xx server errors are retryable
        if (statusCode >= 500) {
            return true;
        }
        // 429 Too Many Requests is retryable
        if (statusCode == 429) {
            return true;
        }
        // Connection failures (status code 0) are retryable
        if (statusCode == 0) {
            return true;
        }
        // 4xx client errors (except 429) are not retryable
        return false;
    }
    
    /**
     * Builds HttpHeaders from the notification's header map.
     */
    private HttpHeaders buildHeaders(Map<String, String> headerMap) {
        HttpHeaders headers = new HttpHeaders();
        if (headerMap != null) {
            headerMap.forEach(headers::set);
        }
        return headers;
    }
}
