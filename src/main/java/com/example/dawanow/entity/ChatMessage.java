package com.example.dawanow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ChatIntent intent;

    @Column(name = "product_ids", length = 255)
    private String productIds;

    @Column(name = "alternative_product_ids", length = 255)
    private String alternativeProductIds;

    @Column(name = "doctor_specializations", length = 500)
    private String doctorSpecializations;

    @Column(name = "emergency_services", length = 100)
    private String emergencyServices;

    @Column(name = "category_ids", length = 255)
    private String categoryIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "performance_period", length = 32)
    private DashboardPeriod performancePeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "performance_metric", length = 32)
    private ChatPerformanceMetric performanceMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "performance_direction", length = 16)
    private ChatPerformanceDirection performanceDirection;

    /** Pharmacy whose admin was authorized when this performance snapshot was created. */
    @Column(name = "performance_pharmacy_id")
    private Long performancePharmacyId;

    /** Compact pharmacistId:count pairs for replaying admin-only performance cards. */
    @Column(name = "offer_ranking_entries", length = 255)
    private String offerRankingEntries;

    /** Compact pharmacistId:count pairs for replaying admin-only performance cards. */
    @Column(name = "successful_order_ranking_entries", length = 255)
    private String successfulOrderRankingEntries;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
