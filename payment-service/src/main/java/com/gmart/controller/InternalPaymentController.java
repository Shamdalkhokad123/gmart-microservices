package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Service-to-service API (X-Internal-Token), used by order-service when a paid order is cancelled. */
@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    public record RefundRequest(String razorpayPaymentId, long amountPaise) {
    }

    private final PaymentService paymentService;

    public InternalPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/refund")
    public ResponseEntity<ApiResponse<Map<String, String>>> refund(@RequestBody RefundRequest request) {
        try {
            String refundId = paymentService.refund(request.razorpayPaymentId(), request.amountPaise());
            return ResponseEntity.ok(ApiResponse.success("Refunded", Map.of("refundId", refundId)));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(ApiResponse.error("Refund failed: " + e.getMessage()));
        }
    }
}
