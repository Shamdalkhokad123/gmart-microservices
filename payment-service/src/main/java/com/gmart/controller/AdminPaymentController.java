package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.entity.PaymentTransaction;
import com.gmart.repository.PaymentTransactionRepository;
import com.gmart.service.PaymentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Admin view of online payments, with a manual "check with Razorpay now". */
@RestController
@RequestMapping("/payments/admin")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class AdminPaymentController {

    private final PaymentTransactionRepository repository;
    private final PaymentService paymentService;
    private final Duration expireAfter;

    public AdminPaymentController(PaymentTransactionRepository repository, PaymentService paymentService,
                                  @Value("${gmart.payments.reconcile.expire-after:P1D}") Duration expireAfter) {
        this.repository = repository;
        this.paymentService = paymentService;
        this.expireAfter = expireAfter;
    }

    /** Latest 200 payments, optionally one status (e.g. REFUND_PENDING). */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<PaymentTransaction>>> list(@RequestParam(required = false) PaymentTransaction.Status status) {
        List<PaymentTransaction> result = status == null
                ? repository.findTop200ByOrderByCreatedAtDesc()
                : repository.findTop200ByStatusOrderByCreatedAtDesc(status);
        return ResponseEntity.ok(ApiResponse.success("Payments", result));
    }

    /** Ask Razorpay about this payment now (and finish it), or retry a pending refund. */
    @PostMapping("/transactions/{razorpayOrderId}/reconcile")
    public ResponseEntity<ApiResponse<Map<String, String>>> reconcile(@PathVariable String razorpayOrderId) {
        PaymentTransaction p = repository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new com.gmart.web.BusinessException("Payment not found", 404));
        String outcome = p.getStatus() == PaymentTransaction.Status.REFUND_PENDING
                ? (paymentService.retryRefund(p) ? "refunded" : "refund still failing")
                : paymentService.reconcile(razorpayOrderId, expireAfter);
        return ResponseEntity.ok(ApiResponse.success(outcome, Map.of("outcome", outcome)));
    }
}
