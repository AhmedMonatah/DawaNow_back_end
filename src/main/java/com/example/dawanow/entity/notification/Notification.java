package com.example.dawanow.entity.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * The notification MESSAGE itself — content only, no recipient/delivery
 * info. One row per logical event, even when that event fans out to many
 * recipients (e.g. one ORDER_IN_AREA notification broadcast to five online
 * pharmacists at the same pharmacy shares a single row here).
 *
 * Delivery/read tracking per-recipient lives in NotificationRecipient.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "notification",
        indexes = {
                @Index(name = "idx_notification_category", columnList = "category"),
                @Index(name = "idx_notification_created_at", columnList = "created_at")
        }
)
public class Notification {

    public enum Category {
        PHARMACY_INVITATION,
        REQUEST_IN_AREA,
        OFFER_ACCEPTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private Category category;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, length = 500)
    private String body;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_payload", columnDefinition = "json")
    private Map<String, Object> dataPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Notification(Category category, String title, String body, Map<String, Object> dataPayload) {
        this.category = category;
        this.title = title;
        this.body = body;
        this.dataPayload = dataPayload;
        this.createdAt = Instant.now();
    }
}