package com.gmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmart.client.ServiceClients;
import com.gmart.client.ServiceClients.Quote;
import com.gmart.client.ServiceClients.StoreInfo;
import com.gmart.dto.PaymentDtos.CreatePaymentRequest;
import com.gmart.entity.PaymentTransaction;
import com.gmart.payment.RazorpayClient;
import com.gmart.repository.PaymentTransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Razorpay Route: the store's share is transferred to its linked account; refunds reverse it. */
class RouteTransferTest {

    private final RazorpayClient razorpay = mock(RazorpayClient.class);
    private final PaymentTransactionRepository repo = mock(PaymentTransactionRepository.class);
    private final ServiceClients services = mock(ServiceClients.class);
    private PaymentService service;

    private static Quote quote(String toPay, String items, String discount) {
        return new Quote(new BigDecimal(toPay), new BigDecimal(items), new BigDecimal(discount), null, false, 1);
    }

    @BeforeEach
    void setUp() {
        when(razorpay.isConfigured()).thenReturn(true);
        service = new PaymentService(razorpay, repo, services, new ObjectMapper(), mock(PlatformTransactionManager.class),
                "INR", "GMart", new SimpleMeterRegistry());
    }

    @Test
    void storeShareIsItemSalesAfterCouponMinusCommission() {
        // items 947, coupon 120 -> 827 x 90% = 744.30 ; delivery/handling fees stay with the platform
        assertThat(PaymentService.storeShare(quote("832.00", "947.00", "120.00"), new BigDecimal("10"), 83200)).isEqualTo(74430L);
        // 0 % commission: the store gets all item sales
        assertThat(PaymentService.storeShare(quote("304.00", "299.00", "0"), BigDecimal.ZERO, 30400)).isEqualTo(29900L);
        // rounding goes in the platform's favour, to the paisa: 33.33 x 87.5 % = 29.16375 -> 29.16
        assertThat(PaymentService.storeShare(quote("68.33", "33.33", "0"), new BigDecimal("12.5"), 6833)).isEqualTo(2916L);
        // never more than was paid
        assertThat(PaymentService.storeShare(quote("1.00", "500.00", "0"), BigDecimal.ZERO, 100)).isEqualTo(100L);
    }

    @Test
    void razorpayOrderCarriesTheTransferToTheLinkedAccount() {
        when(services.quote("asha", null)).thenReturn(quote("832.00", "947.00", "120.00"));
        when(services.store(1)).thenReturn(new StoreInfo(1, "GMart Indiranagar", true, new BigDecimal("10"), "acc_TESTSTORE0001"));
        when(razorpay.createOrder(anyLong(), anyString(), anyString(), anyMap(), anyString(), anyLong()))
                .thenReturn(Map.of("id", "order_X"));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createPayment("asha", new CreatePaymentRequest("Home", null));

        verify(razorpay).createOrder(eq(83200L), eq("INR"), anyString(), anyMap(), eq("acc_TESTSTORE0001"), eq(74430L));
        ArgumentCaptor<PaymentTransaction> saved = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo(1);
        assertThat(saved.getValue().getTransferAmountPaise()).isEqualTo(74430L);
        assertThat(saved.getValue().hasTransfer()).isTrue();
    }

    @Test
    void storeWithoutLinkedAccountIsCashOnly() {
        when(services.quote("asha", null)).thenReturn(quote("304.00", "299.00", "0"));
        when(services.store(1)).thenReturn(new StoreInfo(1, "GMart Indiranagar", true, BigDecimal.TEN, null));
        assertThatThrownBy(() -> service.createPayment("asha", new CreatePaymentRequest("Home", null)))
                .hasMessage("GMart Indiranagar accepts cash on delivery only for now");
        verify(razorpay, never()).createOrder(anyLong(), anyString(), anyString(), anyMap(), any(), anyLong());
    }

    @Test
    void cancellingAPaidOrderReversesTheStoresTransfer() {
        PaymentTransaction p = new PaymentTransaction();
        p.setLinkedAccountId("acc_TESTSTORE0001");
        p.setTransferAmountPaise(74430L);
        when(repo.findFirstByRazorpayPaymentId("pay_1")).thenReturn(Optional.of(p));
        when(razorpay.refund("pay_1", 83200, true)).thenReturn(Map.of("id", "rfnd_1"));

        assertThat(service.refund("pay_1", 83200)).isEqualTo("rfnd_1");
        verify(razorpay).refund("pay_1", 83200, true);
    }
}
