package com.gmart.repository;

import com.gmart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Integer> {

    List<CartItem> findByUserNameAndPaymentStatus(String userName, String paymentStatus);

    List<CartItem> findByUserName(String userName);

    List<CartItem> findByOrderId(Integer orderId);

    Optional<CartItem> findByUserNameAndProductIdAndPaymentStatus(String userName, Integer productId, String paymentStatus);
}
