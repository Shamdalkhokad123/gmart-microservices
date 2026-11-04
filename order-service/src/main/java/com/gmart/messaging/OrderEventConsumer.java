package com.gmart.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmart.service.OrderFulfilmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Plays the role of the store: it receives new orders from Kafka and accepts them.
 * From there OrderFulfilmentService moves the order through packing and delivery.
 */
@Component
@ConditionalOnProperty(name = "gmart.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderFulfilmentService fulfilmentService;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(OrderFulfilmentService fulfilmentService, ObjectMapper objectMapper) {
        this.fulfilmentService = fulfilmentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${gmart.kafka.topic.orders}", groupId = "gmart-store")
    public void onNewOrder(String message) {
        try {
            OrderPlacedEvent event = objectMapper.readValue(message, OrderPlacedEvent.class);
            log.info(">>> [Kafka] Store received order {} from {} (₹{})", event.orderId(), event.username(), event.totalAmount());
            fulfilmentService.confirm(event.orderId());
        } catch (Exception e) {
            log.error("Could not process order message: {}", message, e);
        }
    }

    @KafkaListener(topics = "${gmart.kafka.topic.order-status}", groupId = "gmart-notifications")
    public void onStatusChange(String message) {
        // Hook for SMS / push notifications
        log.info(">>> [Kafka] Order status update: {}", message);
    }
}
