package com.gmart.repository;

import com.gmart.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AddressRepository extends JpaRepository<Address, Integer> {
    List<Address> findByUsernameOrderByDefaultAddressDescAddressIdDesc(String username);
}
