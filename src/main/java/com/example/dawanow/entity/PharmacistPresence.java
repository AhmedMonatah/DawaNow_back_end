package com.example.dawanow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Explicit on-duty presence for a pharmacist.
 * One row per pharmacist (pharmacist_id is both PK and FK).
 * on_duty is set via an explicit toggle endpoint, not inferred from a live
 * socket connection — backed by a heartbeat so a crashed/killed app doesn't
 * stay stuck on_duty=true forever. Heartbeat timeout (currently 5 minutes)
 * is enforced by a scheduled sweep, not by this entity itself.
 */
@Entity
@Getter
@NoArgsConstructor()
@Table(
        name = "pharmacist_presence",
        indexes = {
                // used by the scheduled sweep that flips stale on_duty=true rows to false
                @Index(name = "idx_presence_on_duty_heartbeat", columnList = "is_on_duty, last_heartbeat_at")
        }
)
public class PharmacistPresence {

    @Id
    @Column(name = "pharmacist_id")
    private Long pharmacistId;

    @Column(name = "is_on_duty", nullable = false)
    private boolean onDuty = false;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public PharmacistPresence(Long pharmacistId) {
        this.pharmacistId = pharmacistId;
        this.onDuty = false;
        this.updatedAt = Instant.now();
    }

    public void goOnDuty() {
        this.onDuty = true;
        this.lastHeartbeatAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void goOffDuty() {
        this.onDuty = false;
        this.updatedAt = Instant.now();
    }

    public void heartbeat() {
        this.lastHeartbeatAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isStale(Instant timeoutThreshold) {
        return onDuty
                && lastHeartbeatAt != null
                && lastHeartbeatAt.isBefore(timeoutThreshold);
    }
}