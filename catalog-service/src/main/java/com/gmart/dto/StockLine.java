package com.gmart.dto;

/** One product and quantity to reserve or release. */
public record StockLine(Integer productId, String productName, int quantity) {
}
