package com.example.dawanow.repo;

import com.example.dawanow.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseOrArabicNameContainingIgnoreCaseOrScientificNameContainingIgnoreCase(
            String name,
            String arabicName,
            String scientificName,
            Pageable pageable
    );
}
