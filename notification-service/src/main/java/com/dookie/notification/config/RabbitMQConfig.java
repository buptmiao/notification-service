package com.dookie.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for notification processing.
 * 
 * Sets up:
 * - notification.queue: Main queue for pending notifications
 * - notification.dlq: Dead letter queue for failed notifications
 * - Exchanges and bindings for routing
 * 
 * Requirements: 2.5, 7.1
 */
@Configuration
public class RabbitMQConfig {
    
    // Queue names
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_DLQ = "notification.dlq";
    
    // Exchange names
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String NOTIFICATION_DLX = "notification.dlx";
    
    // Routing keys
    public static final String NOTIFICATION_ROUTING_KEY = "notification.pending";
    public static final String NOTIFICATION_DLQ_ROUTING_KEY = "notification.dead";
    
    // Message TTL for delayed retry (default 60 seconds)
    private static final int DEFAULT_MESSAGE_TTL = 60000;
    
    /**
     * Dead letter exchange for failed notifications.
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(NOTIFICATION_DLX);
    }
    
    /**
     * Dead letter queue for notifications that exceed max retries.
     * Messages in DLQ are persisted and can be manually retried.
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ)
                .build();
    }
    
    /**
     * Binding for dead letter queue to dead letter exchange.
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(NOTIFICATION_DLQ_ROUTING_KEY);
    }
    
    /**
     * Main exchange for notification routing.
     */
    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }
    
    /**
     * Main notification queue for pending notifications.
     * Configured with dead letter exchange for failed messages.
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }
    
    /**
     * Binding for notification queue to notification exchange.
     */
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder
                .bind(notificationQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_ROUTING_KEY);
    }
    
    /**
     * JSON message converter for serializing notification messages.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    /**
     * RabbitTemplate configured with JSON converter.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
