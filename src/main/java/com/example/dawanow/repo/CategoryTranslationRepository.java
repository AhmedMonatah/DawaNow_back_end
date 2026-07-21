package com.example.dawanow.repo;

import com.example.dawanow.entity.CategoryTranslation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, Long> {

    @EntityGraph(attributePaths = "category")
    List<CategoryTranslation> findAllByLang(String lang);

    @EntityGraph(attributePaths = "category")
    List<CategoryTranslation> findAllByCategoryIdInAndLang(Collection<Long> categoryIds, String lang);

    @EntityGraph(attributePaths = "category")
    Optional<CategoryTranslation> findByCategoryIdAndLang(Long categoryId, String lang);
}
