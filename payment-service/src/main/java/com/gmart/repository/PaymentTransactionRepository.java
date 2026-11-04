package com.gmart.repository;

import com.gmart.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByRazorpayOrderId(String razorpayOrderId);

    long countByStatus(PaymentTransaction.Status status);

    /** Payments still waiting, old enough that the browser/webhook should have finished them. */
    List<PaymentTransaction> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(PaymentTransaction.Status status, Date before);

    List<PaymentTransaction> findTop100ByStatusOrderByCreatedAtAsc(PaymentTransaction.Status status);

    List<PaymentTransaction> findTop200ByOrderByCreatedAtDesc();

    List<PaymentTransaction> findTop200ByStatusOrderByCreatedAtDesc(PaymentTransaction.Status status);

    /** Row lock so the browser callback and the webhook can't both create the order. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.razorpayOrderId = :id")
    Optional<PaymentTransaction> lockByRazorpayOrderId(@Param("id") String razorpayOrderId);
}
