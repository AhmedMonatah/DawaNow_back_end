package com.example.dawanow.repo;

import com.example.dawanow.entity.ProductTranslation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, Long> {

    List<ProductTranslation> findAllByLang(String lang);

    @EntityGraph(attributePaths = {"product", "product.category"})
    Optional<ProductTranslation> findByProductIdAndLang(Long productId, String lang);

    @EntityGraph(attributePaths = {"product", "product.category"})
    Page<ProductTranslation> findByLang(String lang, Pageable pageable);

    @EntityGraph(attributePaths = {"product", "product.category"})
    Page<ProductTranslation> findByLangAndProductCategoryId(
            String lang,
            Long categoryId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"product", "product.category"})
    @Query("""
            SELECT translation
            FROM ProductTranslation translation
            WHERE translation.lang = :lang
              AND (
                    LOWER(translation.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(translation.scientificName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(translation.categoryName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(translation.company) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(translation.route) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            """)
    Page<ProductTranslation> search(
            @Param("lang") String lang,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
