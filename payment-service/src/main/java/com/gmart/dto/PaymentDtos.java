package com.gmart.dto;

import jakarta.validation.constraints.NotBlank;

/** Request/response bodies for the Razorpay endpoints. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** Start an online payment for the current cart. */
    public record CreatePaymentRequest(
            @NotBlank(message = "Shipping address is required") String shippingAddr,
            String couponCode) {
    }

    /** Everything the browser needs to open Razorpay Checkout. */
    public record CreatePaymentResponse(
            String keyId,
            String razorpayOrderId,
            long amount,
            String currency,
            String name,
            String description,
            String customerName,
            String email,
            String contact) {
    }

    /** Values from the Razorpay Checkout success handler. */
    public record VerifyPaymentRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature) {
    }
}
