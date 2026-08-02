package com.example.dawanow.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dawanow.entity.PharmacistPresence;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest
class PharmacistPresenceRepositoryTest {

    @Autowired
    private PharmacistPresenceRepository repository;

    @Test
    void flipsStaleOnDutyToOffDuty() {
        Instant now = Instant.now();
        Instant staleThreshold = now.minus(Duration.ofMinutes(10));

        PharmacistPresence fresh = new PharmacistPresence(1L);
        fresh.goOnDuty();
        fresh.heartbeat();
        repository.save(fresh);

        PharmacistPresence stale = new PharmacistPresence(2L);
        stale.goOnDuty();
        ReflectionTestUtils.setField(stale, "lastHeartbeatAt", now.minus(Duration.ofMinutes(30)));
        ReflectionTestUtils.setField(stale, "updatedAt", now.minus(Duration.ofMinutes(30)));
        repository.save(stale);

        int flipped = repository.flipStaleToOffDuty(staleThreshold);

        assertThat(flipped).isEqualTo(1);

        PharmacistPresence stillOnDuty = repository.findById(1L).orElseThrow();
        assertThat(stillOnDuty.isOnDuty()).isTrue();

        PharmacistPresence nowOffDuty = repository.findById(2L).orElseThrow();
        assertThat(nowOffDuty.isOnDuty()).isFalse();
        assertThat(nowOffDuty.getUpdatedAt()).isAfter(staleThreshold);
    }

    @Test
    void flipsNothingWhenNoneAreStale() {
        Instant now = Instant.now();
        Instant staleThreshold = now.minus(Duration.ofMinutes(10));

        PharmacistPresence fresh = new PharmacistPresence(1L);
        fresh.goOnDuty();
        fresh.heartbeat();
        repository.save(fresh);

        int flipped = repository.flipStaleToOffDuty(staleThreshold);

        assertThat(flipped).isZero();
        assertThat(repository.findById(1L).orElseThrow().isOnDuty()).isTrue();
    }
}
