package com.gmart.entity;

import jakarta.persistence.*;

import java.util.Date;

/**
 * One Razorpay order (a payment attempt for the user's cart). The GMart order
 * is only created once the payment is verified and captured.
 */
@Entity
@Table(name = "payment_transactions", indexes = @Index(name = "ux_pay_rzp_order", columnList = "razorpayOrderId", unique = true))
public class PaymentTransaction {

    public enum Status { CREATED, PAID, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String razorpayOrderId;

    @Column(length = 64)
    private String razorpayPaymentId;

    @Column(nullable = false)
    private String username;

    /** Amount in paise, as sent to Razorpay. */
    @Column(nullable = false)
    private Long amountPaise;

    @Column(length = 8)
    private String currency;

    private String couponCode;

    @Column(length = 1000)
    private String shippingAddr;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Status status;

    /** GMart order created from this payment. */
    private Integer orderId;

    @Column(length = 500)
    private String failureReason;

    @Temporal(TemporalType.TIMESTAMP) private Date createdAt;
    @Temporal(TemporalType.TIMESTAMP) private Date updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = new Date(); }

    @PreUpdate
    void onUpdate() { updatedAt = new Date(); }

    public Long getId() { return id; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(Long amountPaise) { this.amountPaise = amountPaise; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getShippingAddr() { return shippingAddr; }
    public void setShippingAddr(String shippingAddr) { this.shippingAddr = shippingAddr; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Date getCreatedAt() { return createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
}
