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
    private final com.gmart.service.OrderStatusService statusService;

    public OrderController(OrderService orderService, com.gmart.service.OrderStatusService statusService) {
        this.orderService = orderService;
        this.statusService = statusService;
    }

    /** Body for moving an order to its next fulfilment step (store staff). */
    public record StatusUpdate(com.gmart.entity.OrderStatus status, String deliveryPartnerName, String deliveryPartnerPhone) {
    }

    /** Store staff: confirm, pack, send out (with rider) and mark delivered. */
    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<OrderDetail>> updateStatus(@PathVariable Integer orderId, @RequestBody StatusUpdate update) {
        if (update == null || update.status() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Status is required"));
        }
        if (update.status() == com.gmart.entity.OrderStatus.CANCELLED) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Use cancel to cancel an order (it handles refunds)"));
        }
        try {
            OrderDetail order = statusService.advance(orderId, update.status(), update.deliveryPartnerName(), update.deliveryPartnerPhone());
            return ResponseEntity.ok(ApiResponse.success("Order updated", order));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
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
