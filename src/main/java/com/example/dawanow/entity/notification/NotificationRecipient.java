package com.example.dawanow.entity.notification;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-recipient delivery + read record for a Notification.
 * One notification can fan out to many recipients (e.g. all online
 * pharmacists at a matched pharmacy) — each gets its own row here,
 * tracking delivery status and read state independently, without
 * duplicating the message content itself.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "notification_recipient",
        indexes = {
                // "my notifications" list + unread count for a given pharmacist
                @Index(name = "idx_notif_recipient_pharmacist_status", columnList = "pharmacist_id, status"),
                // dispatcher/retry sweep polling for PENDING work
                @Index(name = "idx_notif_recipient_status_created", columnList = "status, created_at"),
                // "who else received this broadcast" lookup, e.g. to notify losers
                @Index(name = "idx_notif_recipient_notification", columnList = "notification_id")
        },
        uniqueConstraints = {
                // a given pharmacist shouldn't receive the same notification twice
                @UniqueConstraint(name = "uq_notif_recipient_notification_pharmacist",
                        columnNames = {"notification_id", "pharmacist_id"})
        }
)
public class NotificationRecipient {

    public enum Status {
        PENDING,
        SENT,
        FAILED,
        READ
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    @Column(name = "pharmacist_id", nullable = false)
    private Long pharmacistId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public NotificationRecipient(Notification notification, Long pharmacistId) {
        this.notification = notification;
        this.pharmacistId = pharmacistId;
        this.status = Status.PENDING;
        this.sentAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.status = Status.FAILED;
        this.retryCount++;
    }

    public void markRead() {
        this.status = Status.READ;
        this.readAt = Instant.now();
    }
}