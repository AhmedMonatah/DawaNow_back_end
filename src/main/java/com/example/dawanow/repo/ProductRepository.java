package com.example.dawanow.repo;

import com.example.dawanow.dtos.response.ProductSummaryResponse;
import com.example.dawanow.entity.Product;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    @Query("""
            SELECT product
            FROM Product product
            JOIN FETCH product.category
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
            JOIN FETCH product.category
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

    @Query("""
            SELECT DISTINCT product
            FROM Product product
            JOIN FETCH product.category
            LEFT JOIN FETCH product.translations
            """)
    List<Product> findAllForRetrieval();

    /**
     * Batched version for order display: resolves every product in `ids` in one
     * query with the requested language, falling back to the English columns on
     * Product itself. The one place localization/fallback logic lives for order
     * lines. Order of results is not guaranteed to match `ids` — callers should
     * index by id (see OrderService).
     */
    @Query("""
            SELECT new com.example.dawanow.dtos.response.ProductSummaryResponse(
                p.id,
                coalesce(t.name, p.name),
                coalesce(t.productName, p.productName),
                coalesce(t.strength, p.strength),
                coalesce(t.packSize, p.packSize),
                coalesce(t.form, p.form),
                p.price,
                coalesce(t.scientificName, p.scientificName),
                coalesce(t.company, p.company),
                coalesce(t.route, p.route),
                coalesce(t.description, p.description),
                p.imageUrl)
            FROM Product p
            LEFT JOIN p.translations t ON t.lang = :lang
            WHERE p.id IN :ids
            """)
    List<ProductSummaryResponse> findAllLocalized(
            @Param("ids") List<Long> ids,
            @Param("lang") String lang,
            @Param("defaultLang") String defaultLang
    );
}
