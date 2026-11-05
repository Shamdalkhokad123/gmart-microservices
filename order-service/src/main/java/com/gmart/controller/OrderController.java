package com.gmart.controller;

import com.gmart.web.Errors;
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
    private final com.gmart.service.EarningsService earningsService;

    public OrderController(OrderService orderService, com.gmart.service.OrderStatusService statusService,
                           com.gmart.service.EarningsService earningsService) {
        this.orderService = orderService;
        this.statusService = statusService;
        this.earningsService = earningsService;
    }

    /** Body for moving an order to its next fulfilment step (store staff). */
    public record StatusUpdate(com.gmart.entity.OrderStatus status, String deliveryPartnerName, String deliveryPartnerPhone) {
    }

    /** Store staff: confirm, pack, send out (with rider) and mark delivered. */
    @PutMapping("/{orderId}/status")
    @PreAuthorize(com.gmart.security.Roles.CAN_MANAGE_STORE)
    public ResponseEntity<ApiResponse<OrderDetail>> updateStatus(Authentication authentication, @PathVariable Integer orderId, @RequestBody StatusUpdate update) {
        if (update == null || update.status() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Status is required"));
        }
        if (update.status() == com.gmart.entity.OrderStatus.CANCELLED) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Use cancel to cancel an order (it handles refunds)"));
        }
        try {
            OrderDetail order = statusService.advance(com.gmart.security.CurrentUser.of(authentication), orderId,
                    update.status(), update.deliveryPartnerName(), update.deliveryPartnerPhone());
            return ResponseEntity.ok(ApiResponse.success("Order updated", order));
        } catch (com.gmart.web.BusinessException e) {
            return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
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
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<OrderDetail>>> getMyOrders(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved", orderService.getOrdersForUser(authentication.getName())));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetail>> getOrderById(Authentication authentication, @PathVariable Integer orderId) {
        try {
            OrderDetail order = orderService.getOrderFor(com.gmart.security.CurrentUser.of(authentication), orderId);
            return ResponseEntity.ok(ApiResponse.success("Order retrieved", order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderDetail>> cancel(Authentication authentication, @PathVariable Integer orderId) {
        try {
            OrderDetail order = orderService.cancelOrder(orderId, com.gmart.security.CurrentUser.of(authentication));
            return ResponseEntity.ok(ApiResponse.success("Order cancelled", order));
        } catch (com.gmart.web.BusinessException e) {
            int status = e.getStatus() == 404 || e.getStatus() == 403 ? e.getStatus() : 400;
            return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    /** Super admin: every store's orders. */
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrderDetail>>> getAllOrders() {
        return ResponseEntity.ok(ApiResponse.success("All orders retrieved", orderService.getAllOrders()));
    }

    /** Owner (own store) or super admin (?storeId=): earnings and commission for a period (default: this month). */
    @GetMapping("/store/earnings")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_STORE_OWNER')")
    public ResponseEntity<ApiResponse<com.gmart.service.EarningsService.Report>> earnings(
            Authentication authentication, @RequestParam(required = false) Integer storeId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Earnings",
                earningsService.report(com.gmart.security.CurrentUser.of(authentication), storeId, from, to)));
    }

    /** Store console: owner/staff get their store's orders; super admin passes ?storeId=. */
    @GetMapping("/store")
    @PreAuthorize(com.gmart.security.Roles.CAN_MANAGE_STORE)
    public ResponseEntity<ApiResponse<List<OrderDetail>>> getStoreOrders(Authentication authentication,
                                                                       @RequestParam(required = false) Integer storeId) {
        return ResponseEntity.ok(ApiResponse.success("Store orders",
                orderService.getOrdersForStore(com.gmart.security.CurrentUser.of(authentication), storeId)));
    }
}
