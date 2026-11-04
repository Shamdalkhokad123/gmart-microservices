package com.gmart.service;

import com.gmart.dto.BillDetails;
import com.gmart.entity.CartItem;
import com.gmart.entity.OrderStatus;
import com.gmart.repository.OrderDetailRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for cart pricing: fees, free-delivery threshold and coupons.
 * The client only displays these numbers; checkout recomputes them on the server.
 * All amounts are exact rupees with 2 decimals (BigDecimal), never floating point.
 */
@Service
public class PricingService {

    public static final BigDecimal FREE_DELIVERY_THRESHOLD = rupees(199);
    public static final BigDecimal DELIVERY_FEE = rupees(30);
    public static final BigDecimal HANDLING_FEE = rupees(5);

    public enum CouponType { FLAT, PERCENT, FREE_DELIVERY }

    /**
     * @param value  FLAT: rupees off; PERCENT: percentage; FREE_DELIVERY: unused
     */
    public record Coupon(String code, String title, String description, CouponType type,
                         BigDecimal value, BigDecimal maxDiscount, BigDecimal minOrder, boolean firstOrderOnly) {
    }

    private static final Map<String, Coupon> COUPONS = new LinkedHashMap<>();

    static {
        add(new Coupon("WELCOME100", "₹100 off your first order", "On orders above ₹399. New customers only.",
                CouponType.FLAT, rupees(100), rupees(100), rupees(399), true));
        add(new Coupon("GMART50", "Flat ₹50 off", "On orders above ₹299.",
                CouponType.FLAT, rupees(50), rupees(50), rupees(299), false));
        add(new Coupon("SAVE20", "20% off up to ₹120", "On orders above ₹499.",
                CouponType.PERCENT, BigDecimal.valueOf(20), rupees(120), rupees(499), false));
        add(new Coupon("FREEDEL", "Free delivery", "No minimum order value.",
                CouponType.FREE_DELIVERY, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
    }

    private static void add(Coupon c) {
        COUPONS.put(c.code(), c);
    }

    private final OrderDetailRepository orderDetailRepository;

    public PricingService(OrderDetailRepository orderDetailRepository) {
        this.orderDetailRepository = orderDetailRepository;
    }

    public List<Coupon> getCoupons() {
        return List.copyOf(COUPONS.values());
    }

    public BillDetails calculate(List<CartItem> items, String couponCode, String username) {
        BillDetails bill = new BillDetails();
        BigDecimal itemTotal = BigDecimal.ZERO;
        BigDecimal mrpTotal = BigDecimal.ZERO;
        for (CartItem item : items) {
            BigDecimal price = nz(item.getPrice());
            BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
            itemTotal = itemTotal.add(price.multiply(qty));
            BigDecimal mrp = item.getMrp() != null && item.getMrp().compareTo(price) > 0 ? item.getMrp() : price;
            mrpTotal = mrpTotal.add(mrp.multiply(qty));
        }
        boolean empty = items.isEmpty();

        bill.setItemTotal(money(itemTotal));
        bill.setMrpTotal(money(mrpTotal));
        bill.setProductSavings(money(mrpTotal.subtract(itemTotal)));
        bill.setFreeDeliveryThreshold(FREE_DELIVERY_THRESHOLD);

        BigDecimal deliveryFee = empty || itemTotal.compareTo(FREE_DELIVERY_THRESHOLD) >= 0 ? BigDecimal.ZERO : DELIVERY_FEE;
        if (deliveryFee.signum() == 0 && !empty) {
            bill.setDeliveryFeeWaived(DELIVERY_FEE);
        }
        bill.setAmountForFreeDelivery(money(FREE_DELIVERY_THRESHOLD.subtract(itemTotal).max(BigDecimal.ZERO)));
        bill.setHandlingFee(empty ? money(BigDecimal.ZERO) : HANDLING_FEE);

        // --- Coupon ---
        BigDecimal couponDiscount = BigDecimal.ZERO;
        if (couponCode != null && !couponCode.isBlank() && !empty) {
            String code = couponCode.trim().toUpperCase(Locale.ROOT);
            Coupon coupon = COUPONS.get(code);
            if (coupon == null) {
                bill.setCouponMessage("\"" + code + "\" is not a valid coupon");
            } else if (itemTotal.compareTo(coupon.minOrder()) < 0) {
                bill.setCouponMessage("Add items worth ₹" + whole(coupon.minOrder().subtract(itemTotal)) + " more to use " + code);
            } else if (coupon.type() == CouponType.FREE_DELIVERY && deliveryFee.signum() == 0) {
                bill.setCouponMessage("Delivery is already free on this order");
            } else if (coupon.firstOrderOnly() && username != null
                    && orderDetailRepository.countByUsernameAndStatusNot(username, OrderStatus.CANCELLED) > 0) {
                bill.setCouponMessage(code + " is only valid on your first order");
            } else {
                switch (coupon.type()) {
                    case FLAT -> couponDiscount = coupon.value().min(itemTotal);
                    case PERCENT -> couponDiscount = itemTotal.multiply(coupon.value())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                            .min(coupon.maxDiscount())
                            .setScale(0, RoundingMode.HALF_UP); // whole rupees
                    case FREE_DELIVERY -> {
                        if (deliveryFee.signum() > 0) {
                            bill.setDeliveryFeeWaived(deliveryFee);
                            deliveryFee = BigDecimal.ZERO;
                        }
                    }
                }
                bill.setCouponApplied(true);
                bill.setCouponCode(code);
                bill.setCouponMessage(coupon.type() == CouponType.FREE_DELIVERY
                        ? code + " applied: delivery is free"
                        : code + " applied: you save ₹" + whole(couponDiscount));
            }
        }

        bill.setDeliveryFee(money(deliveryFee));
        bill.setCouponDiscount(money(couponDiscount));
        BigDecimal toPay = empty ? BigDecimal.ZERO
                : itemTotal.add(deliveryFee).add(bill.getHandlingFee()).subtract(couponDiscount);
        bill.setToPay(money(toPay.max(BigDecimal.ZERO)));
        bill.setTotalSavings(money(bill.getProductSavings().add(couponDiscount).add(nz(bill.getDeliveryFeeWaived()))));
        return bill;
    }

    private static BigDecimal rupees(int amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String whole(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
