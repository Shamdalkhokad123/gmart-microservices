package com.gmart.repository;

import com.gmart.entity.ContactDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactDetailRepository extends JpaRepository<ContactDetail, Integer> {
    List<ContactDetail> findAllByOrderByCreatedAtDesc();
}
