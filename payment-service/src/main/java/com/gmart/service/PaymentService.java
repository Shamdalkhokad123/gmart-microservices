package com.gmart.service;

import com.gmart.web.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmart.client.ServiceClients;
import com.gmart.client.ServiceClients.PaidOrderRequest;
import com.gmart.client.ServiceClients.Quote;
import com.gmart.client.ServiceClients.UserContact;
import com.gmart.dto.PaymentDtos.CreatePaymentRequest;
import com.gmart.dto.PaymentDtos.CreatePaymentResponse;
import com.gmart.dto.PaymentDtos.VerifyPaymentRequest;
import com.gmart.entity.PaymentTransaction;
import com.gmart.payment.RazorpayClient;
import com.gmart.repository.PaymentTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Objects;

/**
 * Razorpay flow:
 * 1. {@link #createPayment} asks order-service for the cart total and creates a Razorpay order.
 * 2. The browser opens Razorpay Checkout; on success it calls {@link #verifyPayment}.
 *    The {@code payment.captured} / {@code order.paid} webhook does the same, so the
 *    order is still created if the customer closes the tab right after paying.
 * 3. order-service creates the order only after the payment is verified and captured.
 *    If that fails (stock ran out, cart changed) the payment is refunded.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final RazorpayClient razorpay;
    private final PaymentTransactionRepository paymentRepository;
    private final ServiceClients services;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final String currency;
    private final String storeName;
    private final io.micrometer.core.instrument.MeterRegistry meters;

    public PaymentService(RazorpayClient razorpay, PaymentTransactionRepository paymentRepository,
                          ServiceClients services, ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager,
                          @Value("${razorpay.currency:INR}") String currency,
                          @Value("${razorpay.store-name:GMart}") String storeName,
                          io.micrometer.core.instrument.MeterRegistry meters) {
        this.meters = meters;
        // Alert if above 0: a customer is owed money that Razorpay hasn't returned yet
        io.micrometer.core.instrument.Gauge.builder("gmart.payments.refund_pending", paymentRepository,
                        r -> r.countByStatus(PaymentTransaction.Status.REFUND_PENDING))
                .description("Payments whose refund is failing and being retried")
                .register(meters);
        this.razorpay = razorpay;
        this.paymentRepository = paymentRepository;
        this.services = services;
        this.objectMapper = objectMapper;
        this.tx = new TransactionTemplate(transactionManager);
        this.currency = currency;
        this.storeName = storeName;
    }

    /** Rupees to paise, exactly (Razorpay works in paise). */
    public static long toPaise(java.math.BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    public CreatePaymentResponse createPayment(String username, CreatePaymentRequest request) {
        if (!razorpay.isConfigured()) {
            throw new BusinessException("Online payment is not set up yet. Please choose cash on delivery.");
        }
        Quote quote = services.quote(username, request.couponCode());
        long amountPaise = toPaise(quote.toPay());
        if (amountPaise < 100) {
            throw new BusinessException("Online payments must be at least ₹1");
        }

        String receipt = ("gm_" + System.currentTimeMillis() + "_" + username);
        receipt = receipt.length() > 40 ? receipt.substring(0, 40) : receipt;
        Map<String, Object> rzpOrder = razorpay.createOrder(amountPaise, currency, receipt, Map.of("username", username));

        PaymentTransaction payment = new PaymentTransaction();
        payment.setRazorpayOrderId(String.valueOf(rzpOrder.get("id")));
        payment.setUsername(username);
        payment.setAmountPaise(amountPaise);
        payment.setCurrency(currency);
        payment.setCouponCode(quote.couponApplied() ? quote.couponCode() : null);
        payment.setShippingAddr(request.shippingAddr());
        payment.setStatus(PaymentTransaction.Status.CREATED);
        paymentRepository.save(payment);
        return toResponse(payment);
    }

    /** Details for the pay page; only the owner can see them. */
    public Map<String, Object> getPayment(String username, String razorpayOrderId) {
        PaymentTransaction payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .filter(p -> p.getUsername().equals(username))
                .orElseThrow(() -> new BusinessException("Payment not found"));
        return Map.of(
                "checkout", toResponse(payment),
                "status", payment.getStatus().name(),
                "orderId", payment.getOrderId() == null ? "" : payment.getOrderId(),
                "failureReason", payment.getFailureReason() == null ? "" : payment.getFailureReason());
    }

    /** Called by the browser after Razorpay Checkout reports success. Returns the order. */
    public JsonNode verifyPayment(String username, VerifyPaymentRequest request) {
        PaymentTransaction payment = paymentRepository.findByRazorpayOrderId(request.razorpayOrderId())
                .filter(p -> p.getUsername().equals(username))
                .orElseThrow(() -> new BusinessException("Payment not found"));
        if (!razorpay.isValidPaymentSignature(request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature())) {
            log.warn("Invalid Razorpay signature for order {} (user {})", request.razorpayOrderId(), username);
            throw new BusinessException("Payment verification failed");
        }
        return completePayment(payment.getRazorpayOrderId(), request.razorpayPaymentId());
    }

    /** Razorpay webhook. Returns false when the signature is invalid. */
    public boolean handleWebhook(String rawBody, String signature) {
        if (!razorpay.isValidWebhookSignature(rawBody, signature)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String event = root.path("event").asText();
            if (!"payment.captured".equals(event) && !"order.paid".equals(event)) {
                return true;
            }
            JsonNode entity = root.path("payload").path("payment").path("entity");
            String paymentId = entity.path("id").asText(null);
            String orderId = entity.path("order_id").asText(null);
            if (paymentId == null || orderId == null) return true;

            PaymentTransaction payment = paymentRepository.findByRazorpayOrderId(orderId).orElse(null);
            if (payment == null || (payment.getStatus() != PaymentTransaction.Status.CREATED
                    && payment.getStatus() != PaymentTransaction.Status.EXPIRED)) {
                return true; // not ours, or already handled
            }
            completePayment(orderId, paymentId);
        } catch (Exception e) {
            // Acknowledge anyway; the failure is logged and a refund was attempted.
            log.error("Webhook handling failed", e);
        }
        return true;
    }

    /**
     * Reconciler step for a payment still CREATED after the normal flow should have finished:
     * asks Razorpay whether the customer actually paid. If so, completes it exactly like the
     * browser/webhook would (creates the order, or refunds if that fails). Returns what happened.
     */
    public String reconcile(String razorpayOrderId, java.time.Duration expireAfter) {
        PaymentTransaction payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new BusinessException("Payment not found", 404));
        if (payment.getStatus() != PaymentTransaction.Status.CREATED) {
            return "already " + payment.getStatus();
        }
        payment.setLastCheckedAt(new java.util.Date());
        paymentRepository.save(payment);

        String paid = razorpay.fetchOrderPayments(razorpayOrderId).stream()
                .filter(p -> "captured".equals(p.get("status")) || "authorized".equals(p.get("status")))
                .map(p -> String.valueOf(p.get("id")))
                .findFirst().orElse(null);
        if (paid != null) {
            try {
                completePayment(razorpayOrderId, paid);
                return "completed";
            } catch (RuntimeException e) {
                return "refunded: " + e.getMessage();
            }
        }
        if (payment.getCreatedAt() != null
                && payment.getCreatedAt().toInstant().isBefore(java.time.Instant.now().minus(expireAfter))) {
            payment.setStatus(PaymentTransaction.Status.EXPIRED);
            paymentRepository.save(payment);
            return "expired";
        }
        return "not paid yet";
    }

    /** Reconciler step for REFUND_PENDING: try the refund again. */
    public boolean retryRefund(PaymentTransaction payment) {
        try {
            razorpay.refund(payment.getRazorpayPaymentId(), payment.getAmountPaise());
            payment.setStatus(PaymentTransaction.Status.REFUNDED);
            paymentRepository.save(payment);
            log.info("Pending refund for {} completed after {} attempts", payment.getRazorpayPaymentId(), payment.getRefundAttempts());
            return true;
        } catch (RuntimeException e) {
            payment.setRefundAttempts(payment.getRefundAttempts() + 1);
            paymentRepository.save(payment);
            log.error("Refund for payment {} ({}) still failing after {} attempts: {}", payment.getRazorpayPaymentId(),
                    payment.getRazorpayOrderId(), payment.getRefundAttempts(), e.getMessage());
            return false;
        }
    }

    /** Full refund requested by order-service when a paid order is cancelled. Returns the refund id. */
    public String refund(String razorpayPaymentId, long amountPaise) {
        Map<String, Object> refund = razorpay.refund(razorpayPaymentId, amountPaise);
        return String.valueOf(refund.get("id"));
    }

    /**
     * Confirms the payment with Razorpay, then asks order-service to create the order.
     * Safe to call more than once for the same payment (browser + webhook): the row lock
     * serialises callers and order-service's endpoint is idempotent per Razorpay order.
     */
    private JsonNode completePayment(String razorpayOrderId, String razorpayPaymentId) {
        PaymentTransaction payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new BusinessException("Payment not found"));
        if (payment.getStatus() == PaymentTransaction.Status.PAID) {
            return services.getOrder(payment.getOrderId());
        }
        if (payment.getStatus() == PaymentTransaction.Status.REFUNDED) {
            throw new BusinessException("This payment was refunded: " + payment.getFailureReason());
        }
        if (payment.getStatus() == PaymentTransaction.Status.REFUND_PENDING) {
            throw new BusinessException("This payment is being refunded: " + payment.getFailureReason());
        }
        if (payment.getStatus() == PaymentTransaction.Status.EXPIRED) {
            // The customer paid after we gave up waiting: complete it normally
            payment.setStatus(PaymentTransaction.Status.CREATED);
            paymentRepository.save(payment);
        }

        ensureCaptured(payment, razorpayPaymentId);

        try {
            return tx.execute(status -> {
                PaymentTransaction locked = paymentRepository.lockByRazorpayOrderId(razorpayOrderId).orElseThrow();
                if (locked.getStatus() == PaymentTransaction.Status.PAID) {
                    return services.getOrder(locked.getOrderId()); // the other caller won the race
                }
                JsonNode order = services.createPaidOrder(new PaidOrderRequest(locked.getUsername(), locked.getShippingAddr(),
                        locked.getCouponCode(), razorpayOrderId, razorpayPaymentId, locked.getAmountPaise()));

                locked.setStatus(PaymentTransaction.Status.PAID);
                meters.counter("gmart.payments", "outcome", "paid").increment();
                locked.setRazorpayPaymentId(razorpayPaymentId);
                locked.setOrderId(order.path("orderId").asInt());
                paymentRepository.save(locked);
                return order;
            });
        } catch (RuntimeException e) {
            // Money was captured but the order could not be created: give it back.
            String reason = com.gmart.web.Errors.userMessage(e);
            refundFailedPayment(razorpayOrderId, razorpayPaymentId, reason);
            throw new BusinessException(reason + ". Your payment will be refunded to the original payment method.", 409, e);
        }
    }

    /** Payment must belong to this Razorpay order, be for the right amount, and be captured. */
    private void ensureCaptured(PaymentTransaction payment, String razorpayPaymentId) {
        Map<String, Object> p = razorpay.fetchPayment(razorpayPaymentId);
        if (!Objects.equals(p.get("order_id"), payment.getRazorpayOrderId())) {
            throw new BusinessException("Payment does not match this order");
        }
        long paid = ((Number) p.get("amount")).longValue();
        if (paid != payment.getAmountPaise()) {
            throw new BusinessException("Paid amount does not match the order amount");
        }
        String status = String.valueOf(p.get("status"));
        if ("authorized".equals(status)) {
            // Accounts with auto-capture switched off
            p = razorpay.capturePayment(razorpayPaymentId, paid, payment.getCurrency());
            status = String.valueOf(p.get("status"));
        }
        if (!"captured".equals(status)) {
            throw new BusinessException("Payment was not completed (status: " + status + ")");
        }
    }

    private void refundFailedPayment(String razorpayOrderId, String razorpayPaymentId, String reason) {
        PaymentTransaction payment = paymentRepository.findByRazorpayOrderId(razorpayOrderId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentTransaction.Status.CREATED) {
            return;
        }
        try {
            razorpay.refund(razorpayPaymentId, payment.getAmountPaise());
            payment.setStatus(PaymentTransaction.Status.REFUNDED);
            meters.counter("gmart.payments", "outcome", "refunded_order_failed").increment();
            log.warn("Refunded payment {} for {}: {}", razorpayPaymentId, razorpayOrderId, reason);
        } catch (RuntimeException refundError) {
            // PaymentReconciler retries it; it also shows on the admin Payments page
            payment.setStatus(PaymentTransaction.Status.REFUND_PENDING);
            payment.setRefundAttempts(1);
            meters.counter("gmart.payments", "outcome", "refund_failed").increment();
            log.error("REFUND FAILED for payment {} ({}), will retry. Order failed because: {}", razorpayPaymentId, razorpayOrderId, reason, refundError);
        }
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setFailureReason(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
        paymentRepository.save(payment);
    }

    private CreatePaymentResponse toResponse(PaymentTransaction payment) {
        UserContact user = services.userContact(payment.getUsername());
        return new CreatePaymentResponse(
                razorpay.getKeyId(), payment.getRazorpayOrderId(), payment.getAmountPaise(), payment.getCurrency(),
                storeName, "Grocery order",
                user != null ? user.fullName() : null,
                user != null ? user.email() : null,
                user != null ? user.mobileNo() : null);
    }
}
