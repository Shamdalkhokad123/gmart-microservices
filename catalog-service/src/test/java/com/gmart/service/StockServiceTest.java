package com.gmart.service;

import com.gmart.dto.StockLine;
import com.gmart.entity.Product;
import com.gmart.repository.ProductRepository;
import com.gmart.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against the in-memory h2 profile (no Docker). */
@SpringBootTest(properties = "gmart.seed.demo-data=false")
@ActiveProfiles("h2")
class StockServiceTest {

    @Autowired StockService stock;
    @Autowired ProductRepository products;

    private Product apples;
    private Product milk;

    @BeforeEach
    void setUp() {
        apples = products.save(product("Apples", 5));
        milk = products.save(product("Milk", 2));
    }

    private static Product product(String name, int stock) {
        Product p = new Product(null, name, "", "Test", new BigDecimal("10.00"), null);
        p.setStock(stock);
        return p;
    }

    private int stockOf(Product p) {
        return products.findById(p.getProductId()).orElseThrow().getStock();
    }

    @Test
    void reservesEverythingAndReturnsCurrentPrices() {
        List<Product> reserved = stock.reserve(List.of(
                new StockLine(apples.getProductId(), "Apples", 3),
                new StockLine(milk.getProductId(), "Milk", 2)));
        assertThat(reserved).hasSize(2);
        assertThat(stockOf(apples)).isEqualTo(2);
        assertThat(stockOf(milk)).isZero();
    }

    @Test
    void reservesNothingWhenAnyLineIsShort() {
        assertThatThrownBy(() -> stock.reserve(List.of(
                new StockLine(apples.getProductId(), "Apples", 3),
                new StockLine(milk.getProductId(), "Milk", 5))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only 2 of Milk left")
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(409);
        // the apples line was rolled back too
        assertThat(stockOf(apples)).isEqualTo(5);
        assertThat(stockOf(milk)).isEqualTo(2);
    }

    @Test
    void releaseIsAppliedOnlyOncePerReleaseId() {
        String releaseId = UUID.randomUUID().toString();
        List<StockLine> lines = List.of(new StockLine(apples.getProductId(), "Apples", 4));

        assertThat(stock.release(releaseId, lines)).isTrue();
        assertThat(stock.release(releaseId, lines)).isFalse(); // retry after a timeout
        assertThat(stockOf(apples)).isEqualTo(9);
    }
}
