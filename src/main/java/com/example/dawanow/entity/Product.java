package com.example.dawanow.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    @NotBlank
    @Size(max = 500)
    private String name;

    @Column(name = "product_name", nullable = false, length = 500)
    @NotBlank
    @Size(max = 500)
    private String productName;

    @Column(length = 100)
    @Size(max = 100)
    private String strength;

    @Column(name = "pack_size", length = 100)
    @Size(max = 100)
    private String packSize;

    @Column(nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    private String form;

    @Column(name = "scientific_name", nullable = false, length = 1000)
    @NotBlank
    @Size(max = 1000)
    private String scientificName;

    @Column(name = "scientific_category", nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String scientificCategory;

    @Column(nullable = false, precision = 12, scale = 2)
    @NotNull
    @Positive
    @Digits(integer = 10, fraction = 2)
    private BigDecimal price;

    @Column(name = "image_url", nullable = false, length = 1000)
    @NotBlank
    @Size(max = 1000)
    @Pattern(regexp = "https://\\S+")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    @NotNull
    private Category category;

    @Column(nullable = false, length = 500)
    @NotBlank
    @Size(max = 500)
    private String company;

    @Column(nullable = false, length = 100)
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "EAR|EFF|EYE|INJECTION|MOUTH|ORAL\\.LIQUID|ORAL\\.SOLID|RECTAL|SPRAY|TOPICAL")
    private String route;

    @Column(nullable = false, length = 2000)
    @NotBlank
    @Size(max = 2000)
    private String description;

    @OneToMany(mappedBy = "product")
    private List<OrderItem> orderItems = new ArrayList<>();

    @OneToMany(mappedBy = "product")
    private List<RequestItem> requestItems = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductTranslation> translations = new ArrayList<>();
}
