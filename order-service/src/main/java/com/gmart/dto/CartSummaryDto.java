package com.gmart.dto;

import com.gmart.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;

public class CartSummaryDto {

    private List<CartItem> items;
    private BigDecimal grandTotal;   // amount to pay (kept for older clients)
    private int totalItems;
    private BillDetails bill;
    /** The one store this cart is from (null when empty). */
    private Integer storeId;
    private String storeName;

    public Integer getStoreId() { return storeId; }
    public void setStoreId(Integer storeId) { this.storeId = storeId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }

    public CartSummaryDto() {
    }

    public CartSummaryDto(List<CartItem> items, BillDetails bill, int totalItems) {
        this.items = items;
        this.bill = bill;
        this.grandTotal = bill.getToPay();
        this.totalItems = totalItems;
    }

    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public int getTotalItems() { return totalItems; }
    public void setTotalItems(int totalItems) { this.totalItems = totalItems; }
    public BillDetails getBill() { return bill; }
    public void setBill(BillDetails bill) { this.bill = bill; }
}
