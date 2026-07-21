package com.example.dawanow.service;

import com.example.dawanow.dtos.response.PresenceStatusResponse;
import com.example.dawanow.entity.PharmacistPresence;
import com.example.dawanow.repo.PharmacistPresenceRepository;
import com.example.dawanow.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PresenceService{

    // heartbeat timeout — must stay comfortably longer than the client's
    // heartbeat interval (recommended 60-90s) so one missed beat doesn't
    // falsely flip someone off-duty
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofMinutes(15);

    private final PharmacistPresenceRepository presenceRepository;

    @Transactional
    public PresenceStatusResponse goOnDuty(Long pharmacistId) {
        PharmacistPresence presence = getOrCreate(pharmacistId);
        presence.goOnDuty();
        return PresenceStatusResponse.from(presence);
    }

    @Transactional
    public PresenceStatusResponse goOffDuty(Long pharmacistId) {
        PharmacistPresence presence = getOrCreate(pharmacistId);
        presence.goOffDuty();
        return PresenceStatusResponse.from(presence);
    }

    @Transactional
    public PresenceStatusResponse heartbeat(Long pharmacistId) {
        PharmacistPresence presence = getOrCreate(pharmacistId);
        presence.heartbeat();
        return PresenceStatusResponse.from(presence);
    }

    @Transactional
    public int sweepStalePresences() {
        Instant threshold = Instant.now().minus(HEARTBEAT_TIMEOUT);
        return presenceRepository.flipStaleToOffDuty(threshold);
    }

    private PharmacistPresence getOrCreate(Long pharmacistId) {
        return presenceRepository.findByPharmacistId(pharmacistId)
                .orElseGet(() -> presenceRepository.save(new PharmacistPresence(pharmacistId)));
    }
}
