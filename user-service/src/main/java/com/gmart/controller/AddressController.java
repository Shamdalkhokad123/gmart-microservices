package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.entity.Address;
import com.gmart.repository.AddressRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Saved delivery addresses of the signed-in customer. */
@RestController
@RequestMapping("/addresses")
public class AddressController {

    private final AddressRepository addressRepository;

    public AddressController(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Address>>> list(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Addresses", addressRepository.findByUsernameOrderByDefaultAddressDescAddressIdDesc(auth.getName())));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<Address>> create(Authentication auth, @RequestBody Address address) {
        if (address.getLine1() == null || address.getLine1().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("House / flat and street are required"));
        }
        if (address.getPincode() != null && !address.getPincode().isBlank() && !address.getPincode().matches("\\d{6}")) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Pincode must be 6 digits"));
        }
        List<Address> existing = addressRepository.findByUsernameOrderByDefaultAddressDescAddressIdDesc(auth.getName());
        address.setAddressId(null);
        address.setUsername(auth.getName());
        if (existing.isEmpty()) {
            address.setDefaultAddress(true);
        } else if (address.isDefaultAddress()) {
            existing.forEach(a -> a.setDefaultAddress(false));
            addressRepository.saveAll(existing);
        }
        return ResponseEntity.ok(ApiResponse.success("Address saved", addressRepository.save(address)));
    }

    @PutMapping("/{id}/default")
    @Transactional
    public ResponseEntity<ApiResponse<Address>> makeDefault(Authentication auth, @PathVariable Integer id) {
        List<Address> all = addressRepository.findByUsernameOrderByDefaultAddressDescAddressIdDesc(auth.getName());
        Address target = all.stream().filter(a -> a.getAddressId().equals(id)).findFirst().orElse(null);
        if (target == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Address not found"));
        }
        all.forEach(a -> a.setDefaultAddress(a == target));
        addressRepository.saveAll(all);
        return ResponseEntity.ok(ApiResponse.success("Default address updated", target));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @PathVariable Integer id) {
        Address a = addressRepository.findById(id).orElse(null);
        if (a == null || !a.getUsername().equals(auth.getName())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Address not found"));
        }
        addressRepository.delete(a);
        if (a.isDefaultAddress()) {
            addressRepository.findByUsernameOrderByDefaultAddressDescAddressIdDesc(auth.getName()).stream().findFirst()
                    .ifPresent(next -> { next.setDefaultAddress(true); addressRepository.save(next); });
        }
        return ResponseEntity.ok(ApiResponse.success("Address removed"));
    }
}
