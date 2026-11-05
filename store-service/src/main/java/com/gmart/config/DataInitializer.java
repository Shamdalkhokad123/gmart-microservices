package com.gmart.config;

import com.gmart.entity.Store;
import com.gmart.repository.StoreRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Demo stores for local development (dev profile). Ids 1 and 2 match the owners seeded by
 * user-service and the products seeded by catalog-service. Neither has a Razorpay linked
 * account yet, so they take cash on delivery until the super admin adds one.
 */
@Component
@ConditionalOnProperty(name = "gmart.seed.demo-data", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private final StoreRepository stores;

    public DataInitializer(StoreRepository stores) {
        this.stores = stores;
    }

    @Override
    public void run(String... args) {
        if (stores.count() > 0) return;
        stores.save(store("GMart Indiranagar", "Fresh fruits, vegetables, dairy and daily essentials.", "store_owner",
                "12th Main Road, Indiranagar", "560038", "29ABCDE1234F1Z5"));
        stores.save(store("Fresh Basket Koramangala", "Snacks, sweets, drinks and frozen food.", "fresh_owner",
                "80 Feet Road, Koramangala", "560034", "29FGHIJ5678K1Z2"));
    }

    private static Store store(String name, String description, String owner, String address, String pincode, String gstin) {
        Store s = new Store();
        s.setName(name);
        s.setDescription(description);
        s.setOwnerUsername(owner);
        s.setLegalName(name + " Pvt Ltd");
        s.setPhone("+91 98450 00000");
        s.setAddress(address);
        s.setCity("Bengaluru");
        s.setPincode(pincode);
        s.setGstin(gstin);
        s.setFssai("11223344556677");
        s.setStatus(Store.Status.APPROVED);
        s.setCommissionPercent(BigDecimal.TEN);
        s.setReviewedAt(new Date());
        return s;
    }
}
