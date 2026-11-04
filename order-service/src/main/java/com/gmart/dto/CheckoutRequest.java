package com.gmart.dto;

import jakarta.validation.constraints.NotBlank;

public class CheckoutRequest {

    @NotBlank(message = "Shipping address is required")
    private String shippingAddr;

    @NotBlank(message = "Payment method is required")
    private String trans; // "cc" (Credit Card), "cod" (Cash on Delivery), "netbanking", "upi"

    // Optional card details (for CC)
    private String cardNumber;
    private String cardExpiry;
    private String cardCvv;
    private String cardHolderName;

    // Optional coupon code, re-validated on the server
    private String couponCode;

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public CheckoutRequest() {
    }

    public CheckoutRequest(String shippingAddr, String trans) {
        this.shippingAddr = shippingAddr;
        this.trans = trans;
    }

    public String getShippingAddr() {
        return shippingAddr;
    }

    public void setShippingAddr(String shippingAddr) {
        this.shippingAddr = shippingAddr;
    }

    public String getTrans() {
        return trans;
    }

    public void setTrans(String trans) {
        this.trans = trans;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getCardExpiry() {
        return cardExpiry;
    }

    public void setCardExpiry(String cardExpiry) {
        this.cardExpiry = cardExpiry;
    }

    public String getCardCvv() {
        return cardCvv;
    }

    public void setCardCvv(String cardCvv) {
        this.cardCvv = cardCvv;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }
}
