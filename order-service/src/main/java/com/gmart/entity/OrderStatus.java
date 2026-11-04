package com.gmart.entity;

public enum OrderStatus {
    PLACED, CONFIRMED, PACKED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED;

    /** Customers can cancel until the rider has picked the order up. */
    public boolean isCancellable() {
        return this == PLACED || this == CONFIRMED || this == PACKED;
    }
}
