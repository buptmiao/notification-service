package com.dookie.notification.messaging;

import com.dookie.notification.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer for publishing notification messages to RabbitMQ.
 * Publishes notification IDs to the queue after persistence.
 * 
 * Requirements: 1.4, 7.1
 */
@Component
public class NotificationProducer {
    
    private static final Logger log = LoggerFactory.getLogger(NotificationProducer.class);
    
    private final RabbitTemplate rabbitTemplate;
    
    public NotificationProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    /**
     * Publishes a notification to the queue for processing.
     * Called after the notification is persisted to ensure at-least-once delivery.
     * 
     * @param notificationId The ID of the persisted notification
     */
    public void publishNotification(String notificationId) {
        NotificationMessage message = new NotificationMessage(notificationId);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    message
            );
            log.info("Published notification {} to queue", notificationId);
        } catch (AmqpException e) {
            log.error("Failed to publish notification {} to queue: {}", notificationId, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Publishes a notification for retry with the current retry count.
     * 
     * @param notificationId The ID of the notification to retry
     * @param retryCount The current retry count
     */
    public void publishForRetry(String notificationId, int retryCount) {
        NotificationMessage message = new NotificationMessage(notificationId, retryCount);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    message
            );
            log.info("Published notification {} for retry (attempt #{})", notificationId, retryCount);
        } catch (AmqpException e) {
            log.error("Failed to publish notification {} for retry: {}", notificationId, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Publishes a notification with a delay for scheduled retry.
     * Uses RabbitMQ message properties to set the delay.
     * Note: Requires RabbitMQ delayed message exchange plugin for x-delay header.
     * 
     * @param notificationId The ID of the notification to retry
     * @param retryCount The current retry count
     * @param delayMillis The delay in milliseconds before the message is delivered
     */
    public void publishWithDelay(String notificationId, int retryCount, long delayMillis) {
        NotificationMessage message = new NotificationMessage(notificationId, retryCount);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    message,
                    messagePostProcessor -> {
                        // Set x-delay header for delayed message exchange plugin
                        // If plugin not available, message will be delivered immediately
                        messagePostProcessor.getMessageProperties().setHeader("x-delay", (int) delayMillis);
                        return messagePostProcessor;
                    }
            );
            log.info("Published notification {} for delayed retry in {}ms (attempt #{})", 
                    notificationId, delayMillis, retryCount);
        } catch (AmqpException e) {
            log.error("Failed to publish notification {} for delayed retry: {}", notificationId, e.getMessage());
            throw e;
        }
    }

}
