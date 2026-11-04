package com.gmart.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer addressId;

    @Column(nullable = false)
    private String username;

    /** "Home", "Work", "Other" */
    private String label;
    private String receiverName;
    private String phone;

    /** Flat / house no., floor, building, street */
    @Column(nullable = false, length = 500)
    private String line1;
    private String landmark;
    private String city;
    private String pincode;

    @JsonProperty("isDefault")
    private boolean defaultAddress;

    public Address() {
    }

    /** One-line address stored on orders. */
    @Transient
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder(line1 == null ? "" : line1);
        if (landmark != null && !landmark.isBlank()) sb.append(", Near ").append(landmark);
        if (city != null && !city.isBlank()) sb.append(", ").append(city);
        if (pincode != null && !pincode.isBlank()) sb.append(" - ").append(pincode);
        return sb.toString();
    }

    public Integer getAddressId() { return addressId; }
    public void setAddressId(Integer addressId) { this.addressId = addressId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = line1; }
    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    @JsonProperty("isDefault")
    public boolean isDefaultAddress() { return defaultAddress; }
    @JsonProperty("isDefault")
    public void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
