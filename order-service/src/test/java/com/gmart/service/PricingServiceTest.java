package com.gmart.service;

import com.gmart.dto.BillDetails;
import com.gmart.entity.CartItem;
import com.gmart.entity.OrderStatus;
import com.gmart.repository.OrderDetailRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    private final OrderDetailRepository orders = mock(OrderDetailRepository.class);
    private final PricingService pricing = new PricingService(orders);

    private static CartItem item(String price, String mrp, int qty) {
        CartItem i = new CartItem();
        i.setPrice(new BigDecimal(price));
        i.setMrp(mrp == null ? null : new BigDecimal(mrp));
        i.setQuantity(qty);
        return i;
    }

    private static BigDecimal rs(String v) {
        return new BigDecimal(v);
    }

    @Test
    void smallOrderPaysDeliveryAndHandling() {
        BillDetails bill = pricing.calculate(List.of(item("49", "60", 2)), null, "asha");
        assertThat(bill.getItemTotal()).isEqualByComparingTo(rs("98"));
        assertThat(bill.getDeliveryFee()).isEqualByComparingTo(rs("30"));
        assertThat(bill.getHandlingFee()).isEqualByComparingTo(rs("5"));
        assertThat(bill.getToPay()).isEqualByComparingTo(rs("133"));
        assertThat(bill.getProductSavings()).isEqualByComparingTo(rs("22"));
        assertThat(bill.getAmountForFreeDelivery()).isEqualByComparingTo(rs("101"));
    }

    @Test
    void deliveryIsFreeFromTheThreshold() {
        BillDetails bill = pricing.calculate(List.of(item("199", null, 1)), null, "asha");
        assertThat(bill.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getDeliveryFeeWaived()).isEqualByComparingTo(rs("30"));
        assertThat(bill.getToPay()).isEqualByComparingTo(rs("204"));
    }

    @Test
    void paiseAreExactWithNoFloatingPointDrift() {
        // 0.10 x 3 + 0.20 = 0.50 exactly (float arithmetic would give 0.5000000x)
        BillDetails bill = pricing.calculate(List.of(item("0.10", null, 3), item("0.20", null, 1)), null, null);
        assertThat(bill.getItemTotal()).isEqualByComparingTo(rs("0.50"));
        assertThat(bill.getToPay()).isEqualByComparingTo(rs("35.50"));
        assertThat(bill.getToPay().scale()).isEqualTo(2);
    }

    @Test
    void percentCouponIsCappedAndRoundedToWholeRupees() {
        // 947 x 20% = 189.40, capped at 120
        BillDetails capped = pricing.calculate(List.of(item("549", null, 1), item("199", null, 2)), "save20", "asha");
        assertThat(capped.isCouponApplied()).isTrue();
        assertThat(capped.getCouponCode()).isEqualTo("SAVE20");
        assertThat(capped.getCouponDiscount()).isEqualByComparingTo(rs("120"));
        assertThat(capped.getToPay()).isEqualByComparingTo(rs("832"));

        // 503.50 x 20% = 100.70 -> 101
        BillDetails rounded = pricing.calculate(List.of(item("503.50", null, 1)), "SAVE20", "asha");
        assertThat(rounded.getCouponDiscount()).isEqualByComparingTo(rs("101"));
    }

    @Test
    void couponBelowMinimumIsExplained() {
        BillDetails bill = pricing.calculate(List.of(item("250", null, 1)), "GMART50", "asha");
        assertThat(bill.isCouponApplied()).isFalse();
        assertThat(bill.getCouponMessage()).isEqualTo("Add items worth ₹49 more to use GMART50");
        assertThat(bill.getToPay()).isEqualByComparingTo(rs("255"));
    }

    @Test
    void unknownCouponIsRejected() {
        BillDetails bill = pricing.calculate(List.of(item("250", null, 1)), "FREE1000", "asha");
        assertThat(bill.isCouponApplied()).isFalse();
        assertThat(bill.getCouponMessage()).contains("not a valid coupon");
    }

    @Test
    void freeDeliveryCouponRemovesTheFee() {
        BillDetails bill = pricing.calculate(List.of(item("100", null, 1)), "FREEDEL", "asha");
        assertThat(bill.isCouponApplied()).isTrue();
        assertThat(bill.getDeliveryFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getToPay()).isEqualByComparingTo(rs("105"));
    }

    @Test
    void firstOrderCouponOnlyForNewCustomers() {
        when(orders.countByUsernameAndStatusNot(eq("regular"), any(OrderStatus.class))).thenReturn(3L);
        when(orders.countByUsernameAndStatusNot(eq("newbie"), any(OrderStatus.class))).thenReturn(0L);
        List<CartItem> cart = List.of(item("400", null, 1));

        assertThat(pricing.calculate(cart, "WELCOME100", "regular").isCouponApplied()).isFalse();
        BillDetails bill = pricing.calculate(cart, "WELCOME100", "newbie");
        assertThat(bill.isCouponApplied()).isTrue();
        assertThat(bill.getToPay()).isEqualByComparingTo(rs("305"));
    }

    @Test
    void emptyCartCostsNothing() {
        BillDetails bill = pricing.calculate(List.of(), "SAVE20", "asha");
        assertThat(bill.getToPay()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getHandlingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.isCouponApplied()).isFalse();
    }

    @Test
    void rupeesToPaiseIsExact() {
        assertThat(OrderService.toPaise(rs("832.00"))).isEqualTo(83200L);
        assertThat(OrderService.toPaise(rs("0.29"))).isEqualTo(29L);
        assertThat(OrderService.toPaise(rs("1234.565"))).isEqualTo(123457L);
    }
}
