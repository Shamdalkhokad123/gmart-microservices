package com.gmart.service;

import com.gmart.entity.OrderDetail;
import com.gmart.entity.OrderStatus;
import com.gmart.messaging.OrderEvents;
import com.gmart.repository.OrderDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates the store and delivery rider so orders move along like in a real
 * quick-commerce app: PLACED -> CONFIRMED -> PACKED -> OUT_FOR_DELIVERY -> DELIVERED.
 * Every change is published to Kafka (topic gmart.order-status).
 */
@Service
public class OrderFulfilmentService {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfilmentService.class);

    // Simulated timings, in seconds
    static final int CONFIRM_FALLBACK_SECS = 15;   // if Kafka is off/slow, the store still accepts
    static final int PACKING_SECS = 45;
    static final int RIDER_PICKUP_SECS = 60;
    static final int RIDE_SECS = 150;

    private static final String[][] RIDERS = {
            {"Ravi Kumar", "+91 98450 11223"}, {"Arjun Singh", "+91 99001 44556"},
            {"Mohammed Irfan", "+91 98860 77889"}, {"Suresh Yadav", "+91 97400 22334"},
            {"Deepak Patil", "+91 96200 55667"}
    };

    private final OrderDetailRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderFulfilmentService(OrderDetailRepository orderRepository, ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Called by the Kafka consumer when the store receives the order. */
    @Transactional
    public void confirm(Integer orderId) {
        orderRepository.findById(orderId).ifPresent(o -> {
            if (o.getStatus() == OrderStatus.PLACED) {
                moveTo(o, OrderStatus.CONFIRMED);
            }
        });
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    @Transactional
    public void advanceOrders() {
        List<OrderDetail> active = orderRepository.findByStatusIn(EnumSet.of(
                OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PACKED, OrderStatus.OUT_FOR_DELIVERY));
        long now = System.currentTimeMillis();
        for (OrderDetail o : active) {
            switch (o.getStatus()) {
                case PLACED -> {
                    if (secondsSince(o.getOrderDate(), now) >= CONFIRM_FALLBACK_SECS) moveTo(o, OrderStatus.CONFIRMED);
                }
                case CONFIRMED -> {
                    if (secondsSince(o.getConfirmedAt(), now) >= PACKING_SECS) moveTo(o, OrderStatus.PACKED);
                }
                case PACKED -> {
                    if (secondsSince(o.getPackedAt(), now) >= RIDER_PICKUP_SECS) moveTo(o, OrderStatus.OUT_FOR_DELIVERY);
                }
                case OUT_FOR_DELIVERY -> {
                    if (secondsSince(o.getOutForDeliveryAt(), now) >= RIDE_SECS) moveTo(o, OrderStatus.DELIVERED);
                }
                default -> { }
            }
        }
    }

    private void moveTo(OrderDetail o, OrderStatus next) {
        Date now = new Date();
        o.setStatus(next);
        switch (next) {
            case CONFIRMED -> o.setConfirmedAt(now);
            case PACKED -> o.setPackedAt(now);
            case OUT_FOR_DELIVERY -> {
                o.setOutForDeliveryAt(now);
                String[] rider = RIDERS[ThreadLocalRandom.current().nextInt(RIDERS.length)];
                o.setDeliveryPartnerName(rider[0]);
                o.setDeliveryPartnerPhone(rider[1]);
                o.setEstimatedDeliveryAt(new Date(now.getTime() + RIDE_SECS * 1000L));
            }
            case DELIVERED -> o.setDeliveredAt(now);
            default -> { }
        }
        orderRepository.save(o);
        log.info("Order {} is now {}", o.getOrderId(), next);
        eventPublisher.publishEvent(OrderEvents.status(o));
    }

    private static long secondsSince(Date d, long now) {
        return d == null ? Long.MAX_VALUE : (now - d.getTime()) / 1000;
    }
}
