package com.gmart.dto;

import java.math.BigDecimal;

/** Price breakdown shown in the cart and stored on the order. Amounts in rupees, 2 decimals. */
public class BillDetails {

    private BigDecimal mrpTotal = BigDecimal.ZERO;          // sum of MRP x qty
    private BigDecimal itemTotal = BigDecimal.ZERO;         // sum of selling price x qty
    private BigDecimal productSavings = BigDecimal.ZERO;    // mrpTotal - itemTotal
    private BigDecimal deliveryFee = BigDecimal.ZERO;
    private BigDecimal deliveryFeeWaived = BigDecimal.ZERO; // original fee when it was made free
    private BigDecimal handlingFee = BigDecimal.ZERO;
    private BigDecimal couponDiscount = BigDecimal.ZERO;
    private String couponCode;       // applied code, null when none/invalid
    private String couponMessage;    // success or error text for the UI
    private boolean couponApplied;
    private BigDecimal toPay = BigDecimal.ZERO;
    private BigDecimal totalSavings = BigDecimal.ZERO;
    private BigDecimal freeDeliveryThreshold = BigDecimal.ZERO;
    private BigDecimal amountForFreeDelivery = BigDecimal.ZERO;
    /** Store the cart is from (set on quotes for payment-service). */
    private Integer storeId;

    public Integer getStoreId() { return storeId; }
    public void setStoreId(Integer storeId) { this.storeId = storeId; }

    public BigDecimal getMrpTotal() { return mrpTotal; }
    public void setMrpTotal(BigDecimal mrpTotal) { this.mrpTotal = mrpTotal; }
    public BigDecimal getItemTotal() { return itemTotal; }
    public void setItemTotal(BigDecimal itemTotal) { this.itemTotal = itemTotal; }
    public BigDecimal getProductSavings() { return productSavings; }
    public void setProductSavings(BigDecimal productSavings) { this.productSavings = productSavings; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getDeliveryFeeWaived() { return deliveryFeeWaived; }
    public void setDeliveryFeeWaived(BigDecimal deliveryFeeWaived) { this.deliveryFeeWaived = deliveryFeeWaived; }
    public BigDecimal getHandlingFee() { return handlingFee; }
    public void setHandlingFee(BigDecimal handlingFee) { this.handlingFee = handlingFee; }
    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(BigDecimal couponDiscount) { this.couponDiscount = couponDiscount; }
    public BigDecimal getToPay() { return toPay; }
    public void setToPay(BigDecimal toPay) { this.toPay = toPay; }
    public BigDecimal getTotalSavings() { return totalSavings; }
    public void setTotalSavings(BigDecimal totalSavings) { this.totalSavings = totalSavings; }
    public BigDecimal getFreeDeliveryThreshold() { return freeDeliveryThreshold; }
    public void setFreeDeliveryThreshold(BigDecimal freeDeliveryThreshold) { this.freeDeliveryThreshold = freeDeliveryThreshold; }
    public BigDecimal getAmountForFreeDelivery() { return amountForFreeDelivery; }
    public void setAmountForFreeDelivery(BigDecimal amountForFreeDelivery) { this.amountForFreeDelivery = amountForFreeDelivery; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getCouponMessage() { return couponMessage; }
    public void setCouponMessage(String couponMessage) { this.couponMessage = couponMessage; }
    public boolean isCouponApplied() { return couponApplied; }
    public void setCouponApplied(boolean couponApplied) { this.couponApplied = couponApplied; }
}
