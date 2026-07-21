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
        name = "category_translation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_category_translation_category_lang",
                columnNames = {"category_id", "lang"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class CategoryTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    @NotNull
    private Category category;

    @Column(nullable = false, length = 5)
    @NotBlank
    @Size(max = 5)
    private String lang;

    @Column(nullable = false, length = 255)
    @NotBlank
    @Size(max = 255)
    private String name;
}
