package com.gmart.service;

import com.gmart.entity.OrderDetail;
import com.gmart.entity.OrderStatus;
import com.gmart.repository.OrderDetailRepository;
import com.gmart.security.CurrentUser;
import com.gmart.security.Roles;
import com.gmart.web.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EarningsServiceTest {

    private final OrderDetailRepository repo = mock(OrderDetailRepository.class);
    private final EarningsService service = new EarningsService(repo);
    private static final CurrentUser OWNER = new CurrentUser("owner", Roles.STORE_OWNER, 1);

    private static OrderDetail order(int id, String trans, OrderStatus status, String paymentStatus,
                                     String items, String discount, String delivery, String handling, String total) {
        OrderDetail o = new OrderDetail();
        o.setOrderId(id);
        o.setStoreId(1);
        o.setOrderDate(new Date());
        o.setTrans(trans);
        o.setStatus(status);
        o.setPaymentStatus(paymentStatus);
        o.setItemTotal(new BigDecimal(items));
        o.setDiscount(new BigDecimal(discount));
        o.setDeliveryFee(new BigDecimal(delivery));
        o.setHandlingFee(new BigDecimal(handling));
        o.setTotalAmount(new BigDecimal(total));
        o.setCommissionPercent(new BigDecimal("10"));
        return o;
    }

    @Test
    void onlineIsTransferredAndCashOwesCommissionPlusFees() {
        when(repo.findByStoreIdOrderByOrderDateDesc(1)).thenReturn(List.of(
                // online: 947 items, 120 coupon -> net 827, commission 82.70, store 744.30 (transferred)
                order(1, "razorpay", OrderStatus.CONFIRMED, "PAID", "947.00", "120.00", "0.00", "5.00", "832.00"),
                // cash, delivered: net 150, commission 15, fees 35 -> store owes 50 of the 185 collected
                order(2, "cod", OrderStatus.DELIVERED, "PAID", "150.00", "0.00", "30.00", "5.00", "185.00"),
                // not counted: cancelled, cash not yet delivered, refunded
                order(3, "razorpay", OrderStatus.CANCELLED, "REFUNDED", "100.00", "0", "0", "5", "105.00"),
                order(4, "cod", OrderStatus.PACKED, "PENDING", "100.00", "0", "0", "5", "105.00")));

        EarningsService.Report r = service.report(OWNER, null, LocalDate.now().minusDays(1), LocalDate.now());
        assertThat(r.orders()).isEqualTo(2);
        assertThat(r.itemsNet()).isEqualByComparingTo("977.00");
        assertThat(r.commission()).isEqualByComparingTo("97.70");
        assertThat(r.storeShare()).isEqualByComparingTo("879.30");
        assertThat(r.transferredOnline()).isEqualByComparingTo("744.30");
        assertThat(r.cashCollected()).isEqualByComparingTo("185.00");
        assertThat(r.owedToPlatform()).isEqualByComparingTo("50.00");
    }

    @Test
    void staffCannotSeeEarningsAndOwnersOnlyTheirOwn() {
        assertThatThrownBy(() -> service.report(new CurrentUser("staff", Roles.STAFF, 1), null, null, null))
                .isInstanceOf(BusinessException.class);
        service.report(OWNER, 2, null, null); // the requested store is ignored for owners
        org.mockito.Mockito.verify(repo).findByStoreIdOrderByOrderDateDesc(1);
    }
}
