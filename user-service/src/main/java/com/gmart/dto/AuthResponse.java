package com.gmart.dto;

public class AuthResponse {

    private String token;
    private String type = "Bearer";
    private Long custId;
    private String username;
    private String fullName;
    private String email;
    private String role;
    private String address;
    /** Store an owner/staff member works for (null otherwise). */
    private Integer storeId;

    public Integer getStoreId() { return storeId; }
    public void setStoreId(Integer storeId) { this.storeId = storeId; }

    public AuthResponse() {
    }

    public AuthResponse(String token, Long custId, String username, String fullName, String email, String role, String address) {
        this.token = token;
        this.custId = custId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.address = address;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getCustId() {
        return custId;
    }

    public void setCustId(Long custId) {
        this.custId = custId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
