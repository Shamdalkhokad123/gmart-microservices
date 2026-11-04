package com.gmart.controller;

import com.gmart.web.Errors;
import com.gmart.dto.ApiResponse;
import com.gmart.dto.BillDetails;
import com.gmart.dto.CheckoutRequest;
import com.gmart.entity.OrderDetail;
import com.gmart.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Service-to-service API (X-Internal-Token), used by payment-service. */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    public record QuoteRequest(String username, String couponCode) {
    }

    public record PaidOrderRequest(String username, String shippingAddr, String couponCode,
                                   String razorpayOrderId, String razorpayPaymentId, long amountPaise) {
    }

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** What the customer's cart costs right now (amount to charge online). */
    @PostMapping("/quote")
    public ResponseEntity<ApiResponse<BillDetails>> quote(@RequestBody QuoteRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Quote", orderService.quote(request.username(), request.couponCode())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    /** Create the order for a verified, captured payment. Idempotent per Razorpay order. */
    @PostMapping("/from-payment")
    public ResponseEntity<ApiResponse<OrderDetail>> fromPayment(@RequestBody PaidOrderRequest request) {
        try {
            CheckoutRequest checkout = new CheckoutRequest(request.shippingAddr(), "razorpay");
            checkout.setCouponCode(request.couponCode());
            OrderDetail order = orderService.checkout(request.username(), checkout,
                    new OrderService.OnlinePayment(request.razorpayOrderId(), request.razorpayPaymentId(), request.amountPaise()));
            return ResponseEntity.ok(ApiResponse.success("Order placed", order));
        } catch (Exception e) {
            return ResponseEntity.status(409).body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetail>> get(@PathVariable Integer orderId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Order", orderService.getOrderForUser(orderId, null, true)));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(ApiResponse.error(Errors.userMessage(e)));
        }
    }
}
