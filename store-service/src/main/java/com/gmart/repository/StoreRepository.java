package com.gmart.repository;

import com.gmart.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Integer> {

    List<Store> findByStatusOrderByNameAsc(Store.Status status);

    List<Store> findByStatusOrderByCreatedAtDesc(Store.Status status);

    List<Store> findAllByOrderByCreatedAtDesc();

    List<Store> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);

    boolean existsByOwnerUsernameAndStatusIn(String ownerUsername, Collection<Store.Status> statuses);
}
