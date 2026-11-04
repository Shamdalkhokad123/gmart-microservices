package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.dto.CheckoutRequest;
import com.gmart.entity.OrderDetail;
import com.gmart.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderDetail>> checkout(Authentication authentication,
                                                             @Valid @RequestBody CheckoutRequest request) {
        // Online payments go through /payments/razorpay so an order can't be marked paid without paying
        if (!"cod".equalsIgnoreCase(request.getTrans())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Use online payment (Razorpay) or cash on delivery"));
        }
        try {
            OrderDetail order = orderService.checkout(authentication.getName(), request);
            return ResponseEntity.ok(ApiResponse.success("Order placed successfully", order));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<OrderDetail>>> getMyOrders(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved", orderService.getOrdersForUser(authentication.getName())));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetail>> getOrderById(Authentication authentication, @PathVariable Integer orderId) {
        try {
            OrderDetail order = orderService.getOrderForUser(orderId, authentication.getName(), isAdmin(authentication));
            return ResponseEntity.ok(ApiResponse.success("Order retrieved", order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderDetail>> cancel(Authentication authentication, @PathVariable Integer orderId) {
        try {
            OrderDetail order = orderService.cancelOrder(orderId, authentication.getName(), isAdmin(authentication));
            return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderDetail>>> getAllOrders() {
        return ResponseEntity.ok(ApiResponse.success("All orders retrieved", orderService.getAllOrders()));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
