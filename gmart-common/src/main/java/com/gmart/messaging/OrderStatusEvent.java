package com.gmart.messaging;

import java.util.Date;

/** Published to Kafka topic "gmart.order-status" whenever an order changes state. */
public record OrderStatusEvent(
        Integer orderId,
        String username,
        String status,
        String deliveryPartnerName,
        Date estimatedDeliveryAt,
        Date changedAt) {
}
