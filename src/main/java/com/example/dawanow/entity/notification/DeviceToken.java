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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per device/app-install a pharmacist is logged into.
 * pharmacist_id is intentionally NOT unique — a single pharmacist can have
 * multiple active rows (phone + tablet, or a reinstalled app before the
 * old token is cleaned up). Uniqueness is enforced on the FCM token itself,
 * since a token belongs to exactly one install at a time.
 */
@Entity
@Table(
        name = "device_token",
        indexes = {
                // fan-out lookup: "give me all active tokens for these pharmacists"
                @Index(name = "idx_device_token_pharmacist_active", columnList = "pharmacist_id, is_active"),
                // token rotation lookup: "does this device already have a row?"
                @Index(name = "idx_device_token_pharmacist_device", columnList = "pharmacist_id, device_id"),
                @Index(name = "idx_device_token_fcm_token", columnList = "fcm_token", unique = true)
        }
)

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DeviceToken {

    public enum Platform {
        ANDROID,
        IOS
    }
    public DeviceToken(Long pharmacistId, String fcmToken, Platform platform, String deviceId) {
        this.pharmacistId = pharmacistId;
        this.fcmToken = fcmToken;
        this.platform = platform;
        this.deviceId = deviceId;
        this.active = true;
        this.createdAt = Instant.now();
        this.lastUsedAt = Instant.now();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacist_id", nullable = false)
    private Long pharmacistId;

    @Column(name = "fcm_token", nullable = false, unique = true, length = 255)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private Platform platform;

    @Column(name = "device_id", nullable = false, length = 255)
    private String deviceId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    public void reassignTo(Long pharmacistId) {
        this.pharmacistId = pharmacistId;
        this.active = true;
        this.lastUsedAt = Instant.now();
    }

    public void rotateToken(String newFcmToken) {
        this.fcmToken = newFcmToken;
        this.active = true;
        this.lastUsedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
    }

    public void touch() {
        this.lastUsedAt = Instant.now();
    }

}