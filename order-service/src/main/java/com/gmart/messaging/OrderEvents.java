package com.gmart.messaging;

import com.gmart.entity.OrderDetail;

import java.util.Date;

/** Builds the shared Kafka payloads from order-service's entity. */
public final class OrderEvents {

    private OrderEvents() {
    }

    public static OrderStatusEvent status(OrderDetail o) {
        return new OrderStatusEvent(o.getOrderId(), o.getUsername(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getDeliveryPartnerName(), o.getEstimatedDeliveryAt(), new Date());
    }

    public static OrderPlacedEvent placed(OrderDetail o) {
        return new OrderPlacedEvent(o.getOrderId(), o.getUsername(), o.getTotalAmount(), o.getTrans(),
                o.getShippingAddr(), o.getItemsSummary(), o.getOrderDate());
    }
}
