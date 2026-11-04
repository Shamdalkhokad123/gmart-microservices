package com.gmart.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/** payment-service's internal API: refunds for cancelled orders. */
@Component
public class PaymentClient {

    private final InternalClient http;

    public PaymentClient(@Value("${gmart.services.payment-url}") String baseUrl,
                         @Value("${gmart.internal.token}") String token,
                         ObjectMapper objectMapper) {
        this.http = new InternalClient("Payment service", baseUrl, token, objectMapper);
    }

    /** Full refund; returns the Razorpay refund id. */
    public String refund(String razorpayPaymentId, long amountPaise) {
        Map<String, Object> result = http.post("/internal/payments/refund",
                Map.of("razorpayPaymentId", razorpayPaymentId, "amountPaise", amountPaise),
                new ParameterizedTypeReference<Map<String, Object>>() {});
        return result == null ? null : String.valueOf(result.get("refundId"));
    }
}
