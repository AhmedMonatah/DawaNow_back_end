package com.example.dawanow.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dawanow.repo.CategoryRepository;
import com.example.dawanow.repo.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "dawanow.data.products.import-enabled=true")
class ProductDataInitializerTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void importsProductDataset() {
        assertThat(productRepository.count()).isEqualTo(10_000);
        assertThat(categoryRepository.count()).isGreaterThan(0);
        assertThat(productRepository.findAll())
                .anyMatch(product -> product.getImageUrl() != null)
                .allMatch(product -> product.getPrice().signum() >= 0);
    }
}
