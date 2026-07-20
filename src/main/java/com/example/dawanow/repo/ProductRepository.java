package com.example.dawanow.repo;

import com.example.dawanow.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCategoryId(Long categoryId);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("""
            SELECT product
            FROM Product product
            WHERE (:company IS NULL OR LOWER(product.company) = LOWER(:company))
              AND (:categoryId IS NULL OR product.category.id = :categoryId)
            """)
    Page<Product> findAllFiltered(
            @Param("company") String company,
            @Param("categoryId") Long categoryId,
            Pageable pageable
    );

    @Query("""
            SELECT product
            FROM Product product
            WHERE LOWER(product.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(product.productName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(product.scientificName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(product.scientificCategory) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(product.category.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(product.company) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(product.route) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(product.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<Product> search(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
