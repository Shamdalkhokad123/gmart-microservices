package com.gmart.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "order_details")
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer orderId;

    private Integer cartItemId;

    /** Amount the customer pays (item total + fees - discount), in rupees. */
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    // --- Bill breakdown ---
    @Column(precision = 10, scale = 2) private BigDecimal itemTotal;
    @Column(precision = 10, scale = 2) private BigDecimal mrpTotal;
    @Column(precision = 10, scale = 2) private BigDecimal deliveryFee;
    @Column(precision = 10, scale = 2) private BigDecimal handlingFee;
    @Column(precision = 10, scale = 2) private BigDecimal discount;
    private String couponCode;

    private String username;

    // --- Store (marketplace) ---
    @Column(name = "store_id")
    private Integer storeId;
    @Column(length = 120)
    private String storeName;
    /** Platform commission (percent of item sales) agreed when the order was placed. */
    @Column(precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    public Integer getStoreId() { return storeId; }
    public void setStoreId(Integer storeId) { this.storeId = storeId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public BigDecimal getCommissionPercent() { return commissionPercent; }
    public void setCommissionPercent(BigDecimal commissionPercent) { this.commissionPercent = commissionPercent; }

    @Temporal(TemporalType.TIMESTAMP)
    private Date orderDate;

    @Column(length = 1000)
    private String shippingAddr;

    private String trans; // payment mode: "razorpay", "cod" (older orders: "upi", "cc")

    // --- Payment ---
    @Column(length = 20)
    private String paymentStatus; // "PAID", "PENDING" (cash on delivery), "REFUNDED"
    @Column(length = 64)
    private String razorpayOrderId;
    @Column(length = 64)
    private String razorpayPaymentId;
    @Column(length = 64)
    private String razorpayRefundId;

    @Column(length = 2000)
    private String itemsSummary;

    // --- Live tracking ---
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus status;

    @Temporal(TemporalType.TIMESTAMP) private Date estimatedDeliveryAt;
    @Temporal(TemporalType.TIMESTAMP) private Date confirmedAt;
    @Temporal(TemporalType.TIMESTAMP) private Date packedAt;
    @Temporal(TemporalType.TIMESTAMP) private Date outForDeliveryAt;
    @Temporal(TemporalType.TIMESTAMP) private Date deliveredAt;
    @Temporal(TemporalType.TIMESTAMP) private Date cancelledAt;

    private String deliveryPartnerName;
    private String deliveryPartnerPhone;

    /** Line items, filled in when a single order is fetched. */
    @Transient
    private List<CartItem> items = new ArrayList<>();

    public OrderDetail() {
    }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }
    public Integer getCartItemId() { return cartItemId; }
    public void setCartItemId(Integer cartItemId) { this.cartItemId = cartItemId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getItemTotal() { return itemTotal; }
    public void setItemTotal(BigDecimal itemTotal) { this.itemTotal = itemTotal; }
    public BigDecimal getMrpTotal() { return mrpTotal; }
    public void setMrpTotal(BigDecimal mrpTotal) { this.mrpTotal = mrpTotal; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public void setDeliveryFee(BigDecimal deliveryFee) { this.deliveryFee = deliveryFee; }
    public BigDecimal getHandlingFee() { return handlingFee; }
    public void setHandlingFee(BigDecimal handlingFee) { this.handlingFee = handlingFee; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Date getOrderDate() { return orderDate; }
    public void setOrderDate(Date orderDate) { this.orderDate = orderDate; }
    public String getShippingAddr() { return shippingAddr; }
    public void setShippingAddr(String shippingAddr) { this.shippingAddr = shippingAddr; }
    public String getTrans() { return trans; }
    public void setTrans(String trans) { this.trans = trans; }
    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public String getRazorpayRefundId() { return razorpayRefundId; }
    public void setRazorpayRefundId(String razorpayRefundId) { this.razorpayRefundId = razorpayRefundId; }
    public String getItemsSummary() { return itemsSummary; }
    public void setItemsSummary(String itemsSummary) { this.itemsSummary = itemsSummary; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Date getEstimatedDeliveryAt() { return estimatedDeliveryAt; }
    public void setEstimatedDeliveryAt(Date estimatedDeliveryAt) { this.estimatedDeliveryAt = estimatedDeliveryAt; }
    public Date getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Date confirmedAt) { this.confirmedAt = confirmedAt; }
    public Date getPackedAt() { return packedAt; }
    public void setPackedAt(Date packedAt) { this.packedAt = packedAt; }
    public Date getOutForDeliveryAt() { return outForDeliveryAt; }
    public void setOutForDeliveryAt(Date outForDeliveryAt) { this.outForDeliveryAt = outForDeliveryAt; }
    public Date getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Date deliveredAt) { this.deliveredAt = deliveredAt; }
    public Date getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Date cancelledAt) { this.cancelledAt = cancelledAt; }
    public String getDeliveryPartnerName() { return deliveryPartnerName; }
    public void setDeliveryPartnerName(String deliveryPartnerName) { this.deliveryPartnerName = deliveryPartnerName; }
    public String getDeliveryPartnerPhone() { return deliveryPartnerPhone; }
    public void setDeliveryPartnerPhone(String deliveryPartnerPhone) { this.deliveryPartnerPhone = deliveryPartnerPhone; }
    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }
}
