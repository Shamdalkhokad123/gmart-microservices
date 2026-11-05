package com.gmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.gmart.client.ServiceClients;
import com.gmart.dto.PaymentDtos.VerifyPaymentRequest;
import com.gmart.entity.PaymentTransaction;
import com.gmart.entity.PaymentTransaction.Status;
import com.gmart.payment.RazorpayClient;
import com.gmart.repository.PaymentTransactionRepository;
import com.gmart.web.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Payment completion paths: success, idempotency, refunds when the order can't be created. */
class PaymentServiceTest {

    private final RazorpayClient razorpay = mock(RazorpayClient.class);
    private final PaymentTransactionRepository repo = mock(PaymentTransactionRepository.class);
    private final ServiceClients services = mock(ServiceClients.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private PaymentService service;
    private PaymentTransaction payment;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new PaymentService(razorpay, repo, services, new ObjectMapper(), txManager, "INR", "GMart", new SimpleMeterRegistry());

        payment = new PaymentTransaction();
        payment.setRazorpayOrderId("order_1");
        payment.setUsername("asha");
        payment.setAmountPaise(83200L);
        payment.setCurrency("INR");
        payment.setShippingAddr("Home");
        payment.setStatus(Status.CREATED);
        when(repo.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));
        when(repo.lockByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));
        when(razorpay.isValidPaymentSignature("order_1", "pay_1", "sig")).thenReturn(true);
    }

    private void razorpayReports(String status, long amount) {
        when(razorpay.fetchPayment("pay_1")).thenReturn(Map.of("order_id", "order_1", "amount", amount, "status", status));
    }

    private VerifyPaymentRequest request() {
        return new VerifyPaymentRequest("order_1", "pay_1", "sig");
    }

    @Test
    void capturedPaymentCreatesTheOrder() {
        razorpayReports("captured", 83200);
        when(services.createPaidOrder(any())).thenReturn(JsonNodeFactory.instance.objectNode().put("orderId", 42));

        assertThat(service.verifyPayment("asha", request()).path("orderId").asInt()).isEqualTo(42);
        assertThat(payment.getStatus()).isEqualTo(Status.PAID);
        assertThat(payment.getOrderId()).isEqualTo(42);
        verify(razorpay, never()).refund(any(), anyLong(), anyBoolean());
    }

    @Test
    void authorisedPaymentIsCapturedFirst() {
        razorpayReports("authorized", 83200);
        when(razorpay.capturePayment("pay_1", 83200, "INR")).thenReturn(Map.of("status", "captured"));
        when(services.createPaidOrder(any())).thenReturn(JsonNodeFactory.instance.objectNode().put("orderId", 42));

        service.verifyPayment("asha", request());
        verify(razorpay).capturePayment("pay_1", 83200, "INR");
        assertThat(payment.getStatus()).isEqualTo(Status.PAID);
    }

    @Test
    void secondCallForAPaidPaymentReturnsTheSameOrder() {
        payment.setStatus(Status.PAID);
        payment.setOrderId(42);
        when(services.getOrder(42)).thenReturn(JsonNodeFactory.instance.objectNode().put("orderId", 42));

        assertThat(service.verifyPayment("asha", request()).path("orderId").asInt()).isEqualTo(42);
        verify(services, never()).createPaidOrder(any());
        verify(razorpay, never()).fetchPayment(any());
    }

    @Test
    void forgedSignatureIsRejected() {
        when(razorpay.isValidPaymentSignature("order_1", "pay_1", "sig")).thenReturn(false);
        assertThatThrownBy(() -> service.verifyPayment("asha", request())).hasMessage("Payment verification failed");
        verify(services, never()).createPaidOrder(any());
    }

    @Test
    void anotherCustomersPaymentIsNotFound() {
        assertThatThrownBy(() -> service.verifyPayment("mallory", request())).hasMessage("Payment not found");
    }

    @Test
    void wrongAmountIsRejectedWithoutCreatingAnOrder() {
        razorpayReports("captured", 100);
        assertThatThrownBy(() -> service.verifyPayment("asha", request())).hasMessageContaining("does not match");
        verify(services, never()).createPaidOrder(any());
    }

    @Test
    void orderFailureRefundsTheCustomer() {
        razorpayReports("captured", 83200);
        when(services.createPaidOrder(any())).thenThrow(new BusinessException("Only 1 of Milk left", 409));
        when(razorpay.refund("pay_1", 83200, false)).thenReturn(Map.of("id", "rfnd_1"));

        assertThatThrownBy(() -> service.verifyPayment("asha", request()))
                .hasMessageContaining("Only 1 of Milk left")
                .hasMessageContaining("refunded");
        verify(razorpay).refund("pay_1", 83200, false);
        assertThat(payment.getStatus()).isEqualTo(Status.REFUNDED);
        assertThat(payment.getFailureReason()).isEqualTo("Only 1 of Milk left");
    }

    @Test
    void failedRefundIsKeptForRetry() {
        razorpayReports("captured", 83200);
        when(services.createPaidOrder(any())).thenThrow(new BusinessException("Only 1 of Milk left", 409));
        when(razorpay.refund("pay_1", 83200, false)).thenThrow(new RazorpayClient.PaymentGatewayException("down", null));

        assertThatThrownBy(() -> service.verifyPayment("asha", request())).isInstanceOf(BusinessException.class);
        assertThat(payment.getStatus()).isEqualTo(Status.REFUND_PENDING);

        reset(razorpay);
        when(razorpay.refund("pay_1", 83200, false)).thenReturn(Map.of("id", "rfnd_1"));
        assertThat(service.retryRefund(payment)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(Status.REFUNDED);
    }

    @Test
    void reconcilerCompletesAPaymentWhoseCallbackWasLost() {
        when(razorpay.fetchOrderPayments("order_1")).thenReturn(List.of(Map.of("id", "pay_1", "status", "captured")));
        razorpayReports("captured", 83200);
        when(services.createPaidOrder(any())).thenReturn(JsonNodeFactory.instance.objectNode().put("orderId", 42));

        assertThat(service.reconcile("order_1", Duration.ofDays(1))).isEqualTo("completed");
        assertThat(payment.getStatus()).isEqualTo(Status.PAID);
    }

    @Test
    void reconcilerExpiresOldUnpaidPayments() {
        ReflectionTestUtils.setField(payment, "createdAt", Date.from(Instant.now().minus(Duration.ofDays(2))));
        when(razorpay.fetchOrderPayments("order_1")).thenReturn(List.of(Map.of("id", "pay_x", "status", "failed")));

        assertThat(service.reconcile("order_1", Duration.ofDays(1))).isEqualTo("expired");
        assertThat(payment.getStatus()).isEqualTo(Status.EXPIRED);
        verify(services, never()).createPaidOrder(any());
    }

    @Test
    void rupeesToPaiseIsExact() {
        assertThat(PaymentService.toPaise(new java.math.BigDecimal("832.00"))).isEqualTo(83200L);
    }
}
