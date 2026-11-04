package com.gmart.controller;

import com.gmart.web.Errors;
import com.fasterxml.jackson.databind.JsonNode;
import com.gmart.dto.ApiResponse;
import com.gmart.dto.PaymentDtos.CreatePaymentRequest;
import com.gmart.dto.PaymentDtos.CreatePaymentResponse;
import com.gmart.dto.PaymentDtos.VerifyPaymentRequest;
import com.gmart.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments/razorpay")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Step 1: create a Razorpay order for the current cart. */
    @PostMapping("/order")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> createOrder(Authentication authentication,
                                                                          @Valid @RequestBody CreatePaymentRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Payment created",
                    paymentService.createPayment(authentication.getName(), request)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    /** Pay page: checkout options plus current status of this payment. */
    @GetMapping("/{razorpayOrderId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(Authentication authentication, @PathVariable String razorpayOrderId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Payment", paymentService.getPayment(authentication.getName(), razorpayOrderId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    /** Step 2: verify the Checkout result and place the order. */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<JsonNode>> verify(Authentication authentication,
                                                        @Valid @RequestBody VerifyPaymentRequest request) {
        try {
            JsonNode order = paymentService.verifyPayment(authentication.getName(), request);
            return ResponseEntity.ok(ApiResponse.success("Payment successful, order placed", order));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    /** Razorpay server-to-server webhook (public; authenticated by its HMAC signature). */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        return paymentService.handleWebhook(rawBody, signature)
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
