package com.example.dawanow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "product_translation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_translation_product_lang",
                columnNames = {"product_id", "lang"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ProductTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @NotNull
    private Product product;

    @Column(nullable = false, length = 5)
    @NotBlank
    @Size(max = 5)
    private String lang;

    @Column(nullable = false, length = 500)
    @NotBlank
    @Size(max = 500)
    private String name;

    @Column(name = "scientific_name", nullable = false, length = 1000)
    @NotBlank
    @Size(max = 1000)
    private String scientificName;

    @Column(name = "category_name", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String categoryName;

    @Column(nullable = false, length = 500)
    @NotBlank
    @Size(max = 500)
    private String company;

    @Column(nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String route;
}
