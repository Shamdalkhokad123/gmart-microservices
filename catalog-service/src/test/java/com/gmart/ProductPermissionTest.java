package com.gmart;

import com.gmart.dto.StockLine;
import com.gmart.entity.CatalogStore;
import com.gmart.entity.Product;
import com.gmart.repository.CatalogStoreRepository;
import com.gmart.repository.ProductRepository;
import com.gmart.security.JwtTokenProvider;
import com.gmart.security.Roles;
import com.gmart.service.ProductService;
import com.gmart.service.StockService;
import com.gmart.web.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Products per store: who may change what, and what customers see (h2, no Docker). */
@SpringBootTest(properties = "gmart.seed.demo-data=false")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ProductPermissionTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired ProductRepository products;
    @Autowired CatalogStoreRepository stores;
    @Autowired ProductService productService;
    @Autowired StockService stockService;

    private Product storeA;
    private Product storeB;

    private String bearer(String username, String role, Integer storeId) {
        return "Bearer " + jwt.generateToken(username, role, storeId);
    }

    @BeforeEach
    void setUp() {
        stores.save(new CatalogStore(11, "Store A", true));
        stores.save(new CatalogStore(12, "Store B", true));
        storeA = products.save(product("A-Apples", 11));
        storeB = products.save(product("B-Bananas", 12));
    }

    private static Product product(String name, int store) {
        Product p = new Product(null, name, "", "Fruits", new BigDecimal("50.00"), null);
        p.setStock(10);
        p.setStoreId(store);
        return p;
    }

    private static String json(String name) {
        return "{\"productName\":\"" + name + "\",\"productCategory\":\"Fruits\",\"price\":60,\"stock\":5}";
    }

    @Test
    void ownerEditsOwnProductsOnly() throws Exception {
        String ownerA = bearer("ownerA", Roles.STORE_OWNER, 11);
        mvc.perform(put("/products/" + storeA.getProductId()).header("Authorization", ownerA)
                .contentType(MediaType.APPLICATION_JSON).content(json("A-Apples v2"))).andExpect(status().isOk());
        mvc.perform(put("/products/" + storeB.getProductId()).header("Authorization", ownerA)
                        .contentType(MediaType.APPLICATION_JSON).content(json("hijack")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only manage your own store"));
        mvc.perform(delete("/products/" + storeB.getProductId()).header("Authorization", ownerA)).andExpect(status().isForbidden());
    }

    @Test
    void staffCreateInTheirOwnStoreWhateverTheBodySays() throws Exception {
        String staffA = bearer("staffA", Roles.STAFF, 11);
        mvc.perform(post("/products").header("Authorization", staffA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Sneaky\",\"productCategory\":\"Fruits\",\"price\":10,\"storeId\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(11));
    }

    @Test
    void customersCannotChangeProductsAndOnlySuperAdminsCategories() throws Exception {
        String customer = bearer("cust", Roles.CUSTOMER, null);
        mvc.perform(post("/products").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(json("x")))
                .andExpect(status().isForbidden());
        String owner = bearer("ownerA", Roles.STORE_OWNER, 11);
        mvc.perform(post("/categories").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryName\":\"New\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/categories").header("Authorization", bearer("root", Roles.SUPER_ADMIN, null))
                .contentType(MediaType.APPLICATION_JSON).content("{\"categoryName\":\"New cat " + System.nanoTime() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void suspendedStoreIsHiddenAndCannotSell() throws Exception {
        productService.syncStore(12, "Store B", false);
        mvc.perform(get("/products?storeId=12")).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/products/" + storeB.getProductId())).andExpect(status().isNotFound());
        assertThatThrownBy(() -> stockService.reserve(List.of(new StockLine(storeB.getProductId(), "B-Bananas", 1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("isn't taking orders");
        // its owner still sees the products in the store console
        mvc.perform(get("/products/manage").header("Authorization", bearer("ownerB", Roles.STORE_OWNER, 12)))
                .andExpect(jsonPath("$.data[0].productName").value("B-Bananas"));
        productService.syncStore(12, "Store B", true);
        mvc.perform(get("/products?storeId=12")).andExpect(jsonPath("$.data[0].storeName").value("Store B"));
    }
}
