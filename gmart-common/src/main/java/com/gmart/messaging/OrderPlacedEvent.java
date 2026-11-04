package com.gmart.messaging;

import java.util.Date;

/** Payload published to Kafka topic "gmart.orders" after an order is committed. */
public record OrderPlacedEvent(
        Integer orderId,
        String username,
        float totalAmount,
        String paymentMode,
        String shippingAddr,
        String itemsSummary,
        Date orderDate) {
}
