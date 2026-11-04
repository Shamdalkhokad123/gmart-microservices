package com.gmart.scheduling;

import com.gmart.entity.PaymentTransaction;
import com.gmart.repository.PaymentTransactionRepository;
import com.gmart.service.PaymentService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Safety net for payments the normal flow didn't finish:
 * - CREATED for a while: asks Razorpay whether the customer paid (e.g. the browser closed and
 *   the webhook was lost). Paid -> the order is created (or refunded). Never paid -> EXPIRED.
 * - REFUND_PENDING: retries the refund.
 * Runs every few minutes on one instance at a time.
 */
@Component
public class PaymentReconciler {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciler.class);

    private final PaymentTransactionRepository repository;
    private final PaymentService paymentService;
    private final Duration checkAfter;
    private final Duration expireAfter;

    public PaymentReconciler(PaymentTransactionRepository repository, PaymentService paymentService,
                             @Value("${gmart.payments.reconcile.check-after:PT15M}") Duration checkAfter,
                             @Value("${gmart.payments.reconcile.expire-after:P1D}") Duration expireAfter) {
        this.repository = repository;
        this.paymentService = paymentService;
        this.checkAfter = checkAfter;
        this.expireAfter = expireAfter;
    }

    @Scheduled(fixedDelayString = "${gmart.payments.reconcile.every-ms:300000}", initialDelay = 60000)
    @SchedulerLock(name = "payment-reconciler", lockAtMostFor = "PT10M")
    public void run() {
        Date before = Date.from(Instant.now().minus(checkAfter));
        for (PaymentTransaction p : repository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(PaymentTransaction.Status.CREATED, before)) {
            if (p.getLastCheckedAt() != null && p.getLastCheckedAt().after(before)) {
                continue; // checked recently
            }
            try {
                String outcome = paymentService.reconcile(p.getRazorpayOrderId(), expireAfter);
                if (!"not paid yet".equals(outcome)) {
                    log.info("Reconciled payment {}: {}", p.getRazorpayOrderId(), outcome);
                }
            } catch (RuntimeException e) {
                log.warn("Could not reconcile payment {}: {}", p.getRazorpayOrderId(), e.getMessage());
            }
        }
        for (PaymentTransaction p : repository.findTop100ByStatusOrderByCreatedAtAsc(PaymentTransaction.Status.REFUND_PENDING)) {
            paymentService.retryRefund(p);
        }
    }
}
