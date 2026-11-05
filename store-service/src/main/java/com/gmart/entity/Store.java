package com.gmart.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Date;

/** A business selling on GMart. Created by an application; visible once the super admin approves it. */
@Entity
@Table(name = "stores")
public class Store {

    public enum Status { PENDING, APPROVED, REJECTED, SUSPENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    /** The applicant; becomes ROLE_STORE_OWNER on approval. */
    @Column(nullable = false)
    private String ownerUsername;

    @Column(length = 120)
    private String legalName;

    @Column(length = 20)
    private String phone;

    private String email;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 10)
    private String pincode;

    @Column(length = 20)
    private String gstin;

    @Column(length = 20)
    private String fssai;

    /** Owner can close the store temporarily (no new orders). */
    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    /** Reason shown to the owner when rejected or suspended. */
    @Column(length = 500)
    private String reviewNote;

    /** Platform commission on the store's item sales, in percent. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent = BigDecimal.TEN;

    /** Razorpay Route linked account (acc_...) that receives the store's share of online payments. */
    @Column(length = 40)
    private String razorpayAccountId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    private Date reviewedAt;

    /** Customers see a store only when it is approved. */
    @JsonIgnore
    public boolean isVisible() {
        return status == Status.APPROVED;
    }

    /** Online payment needs a linked account to pay the owner. */
    public boolean isOnlinePayments() {
        return razorpayAccountId != null && !razorpayAccountId.isBlank();
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getFssai() { return fssai; }
    public void setFssai(String fssai) { this.fssai = fssai; }
    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public BigDecimal getCommissionPercent() { return commissionPercent; }
    public void setCommissionPercent(BigDecimal commissionPercent) { this.commissionPercent = commissionPercent; }
    public String getRazorpayAccountId() { return razorpayAccountId; }
    public void setRazorpayAccountId(String razorpayAccountId) { this.razorpayAccountId = razorpayAccountId; }
    public Date getCreatedAt() { return createdAt; }
    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }
}
