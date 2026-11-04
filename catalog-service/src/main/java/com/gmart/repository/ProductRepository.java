package com.gmart.repository;

import com.gmart.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByProductCategoryIgnoreCase(String productCategory);
    List<Product> findByProductNameContainingIgnoreCaseOrProductDescContainingIgnoreCase(String name, String desc);

    /** Row lock for stock changes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :id")
    Optional<Product> lockById(@Param("id") Integer id);
}
