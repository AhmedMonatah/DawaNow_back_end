package com.example.dawanow.repo;

import com.example.dawanow.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByPharmacyId(Long pharmacyId, Pageable pageable);

    boolean existsByOfferId(Long offerId);
}
