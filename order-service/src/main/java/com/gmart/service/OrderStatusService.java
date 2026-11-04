package com.gmart.service;

import com.gmart.entity.OrderDetail;
import com.gmart.entity.OrderStatus;
import com.gmart.messaging.OrderEvents;
import com.gmart.repository.CartItemRepository;
import com.gmart.repository.OrderDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;

/**
 * Moves an order through fulfilment: PLACED -> CONFIRMED -> PACKED -> OUT_FOR_DELIVERY -> DELIVERED.
 * Driven by store staff from the admin Orders page (and, in development only, by DeliverySimulator).
 * Every change is published to Kafka (topic gmart.order-status).
 */
@Service
public class OrderStatusService {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusService.class);

    /** The only allowed next step from each state. Cancelling has its own flow (with refund). */
    private static final Map<OrderStatus, OrderStatus> NEXT = Map.of(
            OrderStatus.PLACED, OrderStatus.CONFIRMED,
            OrderStatus.CONFIRMED, OrderStatus.PACKED,
            OrderStatus.PACKED, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);

    private final OrderDetailRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int rideMinutes;

    public OrderStatusService(OrderDetailRepository orderRepository, CartItemRepository cartItemRepository,
                              ApplicationEventPublisher eventPublisher,
                              @Value("${gmart.delivery.ride-minutes:20}") int rideMinutes) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.eventPublisher = eventPublisher;
        this.rideMinutes = rideMinutes;
    }

    /**
     * Advance an order one step. {@code riderName}/{@code riderPhone} are required when
     * sending it out for delivery.
     */
    @Transactional
    public OrderDetail advance(Integer orderId, OrderStatus next, String riderName, String riderPhone) {
        OrderDetail order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        OrderStatus current = order.getStatus() == null ? OrderStatus.PLACED : order.getStatus();
        if (current == next) {
            return withItems(order); // already there (double click, retry)
        }
        if (NEXT.get(current) != next) {
            throw new IllegalStateException("Order #" + orderId + " is " + current + " and cannot move to " + next);
        }

        Date now = new Date();
        order.setStatus(next);
        switch (next) {
            case CONFIRMED -> order.setConfirmedAt(now);
            case PACKED -> order.setPackedAt(now);
            case OUT_FOR_DELIVERY -> {
                if (isBlank(riderName) || isBlank(riderPhone)) {
                    throw new IllegalArgumentException("Delivery partner name and phone are required");
                }
                order.setDeliveryPartnerName(riderName.trim());
                order.setDeliveryPartnerPhone(riderPhone.trim());
                order.setOutForDeliveryAt(now);
                order.setEstimatedDeliveryAt(new Date(now.getTime() + rideMinutes * 60_000L));
            }
            case DELIVERED -> {
                order.setDeliveredAt(now);
                if ("PENDING".equals(order.getPaymentStatus())) {
                    order.setPaymentStatus("PAID"); // cash collected on delivery
                }
            }
            default -> { }
        }
        orderRepository.save(order);
        log.info("Order {} is now {}", orderId, next);
        eventPublisher.publishEvent(OrderEvents.status(order));
        return withItems(order);
    }

    private OrderDetail withItems(OrderDetail order) {
        order.setItems(cartItemRepository.findByOrderId(order.getOrderId()));
        return order;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
