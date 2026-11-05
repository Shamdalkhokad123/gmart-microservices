package com.gmart.repository;

import com.gmart.entity.OrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Integer> {
    List<OrderDetail> findByUsernameOrderByOrderDateDesc(String username);
    List<OrderDetail> findAllByOrderByOrderDateDesc();

    List<OrderDetail> findByStoreIdOrderByOrderDateDesc(Integer storeId);

    List<OrderDetail> findByStatusIn(java.util.Collection<com.gmart.entity.OrderStatus> statuses);

    java.util.Optional<OrderDetail> findByRazorpayOrderId(String razorpayOrderId);

    long countByUsernameAndStatusNot(String username, com.gmart.entity.OrderStatus status);
}
