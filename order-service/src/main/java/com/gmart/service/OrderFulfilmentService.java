package com.gmart.service;

import com.gmart.entity.OrderDetail;
import com.gmart.entity.OrderStatus;
import com.gmart.repository.OrderDetailRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DEVELOPMENT ONLY: pretends to be the store and a rider so orders move along by
 * themselves while testing. Enabled with gmart.fulfilment.simulate=true (dev profile).
 * In production, staff move orders from the admin Orders page (OrderStatusService).
 */
@Service
@ConditionalOnProperty(name = "gmart.fulfilment.simulate", havingValue = "true")
public class OrderFulfilmentService {

    // Simulated timings, in seconds
    static final int CONFIRM_FALLBACK_SECS = 15;
    static final int PACKING_SECS = 45;
    static final int RIDER_PICKUP_SECS = 60;
    static final int RIDE_SECS = 150;

    private static final String[][] RIDERS = {
            {"Ravi Kumar", "+91 98450 11223"}, {"Arjun Singh", "+91 99001 44556"},
            {"Mohammed Irfan", "+91 98860 77889"}, {"Suresh Yadav", "+91 97400 22334"},
            {"Deepak Patil", "+91 96200 55667"}
    };

    private final OrderDetailRepository orderRepository;
    private final OrderStatusService statusService;

    public OrderFulfilmentService(OrderDetailRepository orderRepository, OrderStatusService statusService) {
        this.orderRepository = orderRepository;
        this.statusService = statusService;
    }

    /** Called by the Kafka consumer when the (simulated) store receives the order. */
    public void confirm(Integer orderId) {
        orderRepository.findById(orderId).ifPresent(o -> {
            if (o.getStatus() == OrderStatus.PLACED) {
                statusService.advance(orderId, OrderStatus.CONFIRMED, null, null);
            }
        });
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void advanceOrders() {
        List<OrderDetail> active = orderRepository.findByStatusIn(EnumSet.of(
                OrderStatus.PLACED, OrderStatus.CONFIRMED, OrderStatus.PACKED, OrderStatus.OUT_FOR_DELIVERY));
        long now = System.currentTimeMillis();
        for (OrderDetail o : active) {
            switch (o.getStatus()) {
                case PLACED -> {
                    if (secondsSince(o.getOrderDate(), now) >= CONFIRM_FALLBACK_SECS) step(o, OrderStatus.CONFIRMED);
                }
                case CONFIRMED -> {
                    if (secondsSince(o.getConfirmedAt(), now) >= PACKING_SECS) step(o, OrderStatus.PACKED);
                }
                case PACKED -> {
                    if (secondsSince(o.getPackedAt(), now) >= RIDER_PICKUP_SECS) step(o, OrderStatus.OUT_FOR_DELIVERY);
                }
                case OUT_FOR_DELIVERY -> {
                    if (secondsSince(o.getOutForDeliveryAt(), now) >= RIDE_SECS) step(o, OrderStatus.DELIVERED);
                }
                default -> { }
            }
        }
    }

    private void step(OrderDetail o, OrderStatus next) {
        String[] rider = RIDERS[ThreadLocalRandom.current().nextInt(RIDERS.length)];
        try {
            statusService.advance(o.getOrderId(), next, rider[0], rider[1]);
        } catch (RuntimeException ignored) {
            // the order changed meanwhile (e.g. cancelled); try again next tick
        }
    }

    private static long secondsSince(Date d, long now) {
        return d == null ? Long.MAX_VALUE : (now - d.getTime()) / 1000;
    }
}
