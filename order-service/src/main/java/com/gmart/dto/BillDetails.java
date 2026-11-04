package com.gmart.dto;

/** Price breakdown shown in the cart and stored on the order. */
public class BillDetails {

    private float mrpTotal;          // sum of MRP x qty
    private float itemTotal;         // sum of selling price x qty
    private float productSavings;    // mrpTotal - itemTotal
    private float deliveryFee;
    private float deliveryFeeWaived; // original fee when it was made free
    private float handlingFee;
    private float couponDiscount;
    private String couponCode;       // applied code, null when none/invalid
    private String couponMessage;    // success or error text for the UI
    private boolean couponApplied;
    private float toPay;
    private float totalSavings;
    private float freeDeliveryThreshold;
    private float amountForFreeDelivery;

    public float getMrpTotal() { return mrpTotal; }
    public void setMrpTotal(float mrpTotal) { this.mrpTotal = mrpTotal; }
    public float getItemTotal() { return itemTotal; }
    public void setItemTotal(float itemTotal) { this.itemTotal = itemTotal; }
    public float getProductSavings() { return productSavings; }
    public void setProductSavings(float productSavings) { this.productSavings = productSavings; }
    public float getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(float deliveryFee) { this.deliveryFee = deliveryFee; }
    public float getDeliveryFeeWaived() { return deliveryFeeWaived; }
    public void setDeliveryFeeWaived(float deliveryFeeWaived) { this.deliveryFeeWaived = deliveryFeeWaived; }
    public float getHandlingFee() { return handlingFee; }
    public void setHandlingFee(float handlingFee) { this.handlingFee = handlingFee; }
    public float getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(float couponDiscount) { this.couponDiscount = couponDiscount; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getCouponMessage() { return couponMessage; }
    public void setCouponMessage(String couponMessage) { this.couponMessage = couponMessage; }
    public boolean isCouponApplied() { return couponApplied; }
    public void setCouponApplied(boolean couponApplied) { this.couponApplied = couponApplied; }
    public float getToPay() { return toPay; }
    public void setToPay(float toPay) { this.toPay = toPay; }
    public float getTotalSavings() { return totalSavings; }
    public void setTotalSavings(float totalSavings) { this.totalSavings = totalSavings; }
    public float getFreeDeliveryThreshold() { return freeDeliveryThreshold; }
    public void setFreeDeliveryThreshold(float freeDeliveryThreshold) { this.freeDeliveryThreshold = freeDeliveryThreshold; }
    public float getAmountForFreeDelivery() { return amountForFreeDelivery; }
    public void setAmountForFreeDelivery(float amountForFreeDelivery) { this.amountForFreeDelivery = amountForFreeDelivery; }
}
