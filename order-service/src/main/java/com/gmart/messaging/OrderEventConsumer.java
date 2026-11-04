package com.gmart.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmart.service.OrderFulfilmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Receives new orders and status changes from Kafka. This is the hook for store
 * notifications (a tablet/printer in the store, SMS to the customer). In development
 * the simulated store also auto-confirms the order here.
 */
@Component
@ConditionalOnProperty(name = "gmart.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final ObjectProvider<OrderFulfilmentService> simulator;
    private final ObjectMapper objectMapper;

    public OrderEventConsumer(ObjectProvider<OrderFulfilmentService> simulator, ObjectMapper objectMapper) {
        this.simulator = simulator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${gmart.kafka.topic.orders}", groupId = "gmart-store")
    public void onNewOrder(String message) {
        try {
            OrderPlacedEvent event = objectMapper.readValue(message, OrderPlacedEvent.class);
            log.info(">>> [Kafka] Store received order {} from {} (₹{})", event.orderId(), event.username(), event.totalAmount());
            // Only the development simulator confirms automatically; real staff confirm in the admin app
            simulator.ifAvailable(sim -> sim.confirm(event.orderId()));
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
