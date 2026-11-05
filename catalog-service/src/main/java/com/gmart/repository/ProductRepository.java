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

    /**
     * What customers can see: products of active stores, optionally one store, one category
     * and/or a search text (name or description).
     */
    @Query("""
            select p from Product p
            where p.storeId in (select s.id from CatalogStore s where s.active = true)
              and (:storeId is null or p.storeId = :storeId)
              and (:category is null or lower(p.productCategory) = lower(:category))
              and (:q is null or lower(p.productName) like lower(concat('%', :q, '%'))
                              or lower(p.productDesc) like lower(concat('%', :q, '%')))
            order by p.productId""")
    List<Product> findVisible(@Param("storeId") Integer storeId, @Param("category") String category, @Param("q") String q);

    @Query("select p from Product p where p.productId = :id and p.storeId in (select s.id from CatalogStore s where s.active = true)")
    Optional<Product> findVisibleById(@Param("id") Integer id);

    /** Store console: everything a store sells, visible or not. */
    List<Product> findByStoreIdOrderByProductIdAsc(Integer storeId);

    /** Row lock for stock changes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :id")
    Optional<Product> lockById(@Param("id") Integer id);
}
