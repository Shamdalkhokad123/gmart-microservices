package com.gmart.service;

import com.gmart.dto.BillDetails;
import com.gmart.entity.CartItem;
import com.gmart.entity.OrderStatus;
import com.gmart.repository.OrderDetailRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for cart pricing: fees, free-delivery threshold and coupons.
 * The client only displays these numbers; checkout recomputes them on the server.
 */
@Service
public class PricingService {

    public static final float FREE_DELIVERY_THRESHOLD = 199f;
    public static final float DELIVERY_FEE = 30f;
    public static final float HANDLING_FEE = 5f;

    public enum CouponType { FLAT, PERCENT, FREE_DELIVERY }

    public record Coupon(String code, String title, String description, CouponType type,
                         float value, float maxDiscount, float minOrder, boolean firstOrderOnly) {
    }

    private static final Map<String, Coupon> COUPONS = new LinkedHashMap<>();

    static {
        add(new Coupon("WELCOME100", "₹100 off your first order", "On orders above ₹399. New customers only.",
                CouponType.FLAT, 100, 100, 399, true));
        add(new Coupon("GMART50", "Flat ₹50 off", "On orders above ₹299.",
                CouponType.FLAT, 50, 50, 299, false));
        add(new Coupon("SAVE20", "20% off up to ₹120", "On orders above ₹499.",
                CouponType.PERCENT, 20, 120, 499, false));
        add(new Coupon("FREEDEL", "Free delivery", "No minimum order value.",
                CouponType.FREE_DELIVERY, 0, 0, 0, false));
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
        float itemTotal = 0, mrpTotal = 0;
        for (CartItem item : items) {
            itemTotal += item.getPrice() * item.getQuantity();
            float mrp = item.getMrp() != null && item.getMrp() > item.getPrice() ? item.getMrp() : item.getPrice();
            mrpTotal += mrp * item.getQuantity();
        }

        bill.setItemTotal(round(itemTotal));
        bill.setMrpTotal(round(mrpTotal));
        bill.setProductSavings(round(mrpTotal - itemTotal));
        bill.setFreeDeliveryThreshold(FREE_DELIVERY_THRESHOLD);

        float deliveryFee = items.isEmpty() || itemTotal >= FREE_DELIVERY_THRESHOLD ? 0 : DELIVERY_FEE;
        if (deliveryFee == 0 && !items.isEmpty()) {
            bill.setDeliveryFeeWaived(DELIVERY_FEE);
        }
        bill.setAmountForFreeDelivery(round(Math.max(0, FREE_DELIVERY_THRESHOLD - itemTotal)));
        bill.setHandlingFee(items.isEmpty() ? 0 : HANDLING_FEE);

        // --- Coupon ---
        float couponDiscount = 0;
        if (couponCode != null && !couponCode.isBlank() && !items.isEmpty()) {
            String code = couponCode.trim().toUpperCase(Locale.ROOT);
            Coupon coupon = COUPONS.get(code);
            if (coupon == null) {
                bill.setCouponMessage("\"" + code + "\" is not a valid coupon");
            } else if (itemTotal < coupon.minOrder()) {
                bill.setCouponMessage(String.format("Add items worth ₹%.0f more to use %s", coupon.minOrder() - itemTotal, code));
            } else if (coupon.type() == CouponType.FREE_DELIVERY && deliveryFee == 0) {
                bill.setCouponMessage("Delivery is already free on this order");
            } else if (coupon.firstOrderOnly() && username != null
                    && orderDetailRepository.countByUsernameAndStatusNot(username, OrderStatus.CANCELLED) > 0) {
                bill.setCouponMessage(code + " is only valid on your first order");
            } else {
                switch (coupon.type()) {
                    case FLAT -> couponDiscount = Math.min(coupon.value(), itemTotal);
                    case PERCENT -> couponDiscount = Math.round(Math.min(itemTotal * coupon.value() / 100f, coupon.maxDiscount()));
                    case FREE_DELIVERY -> {
                        if (deliveryFee > 0) {
                            bill.setDeliveryFeeWaived(deliveryFee);
                            deliveryFee = 0;
                        }
                    }
                }
                bill.setCouponApplied(true);
                bill.setCouponCode(code);
                bill.setCouponMessage(coupon.type() == CouponType.FREE_DELIVERY
                        ? code + " applied: delivery is free"
                        : String.format("%s applied: you save ₹%.0f", code, couponDiscount));
            }
        }

        bill.setDeliveryFee(deliveryFee);
        bill.setCouponDiscount(round(couponDiscount));
        float toPay = items.isEmpty() ? 0 : itemTotal + deliveryFee + bill.getHandlingFee() - couponDiscount;
        bill.setToPay(round(Math.max(0, toPay)));
        bill.setTotalSavings(round(bill.getProductSavings() + couponDiscount + bill.getDeliveryFeeWaived()));
        return bill;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }
}
