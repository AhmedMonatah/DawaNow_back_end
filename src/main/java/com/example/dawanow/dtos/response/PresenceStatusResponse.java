package com.example.dawanow.dtos.response;


import com.example.dawanow.entity.PharmacistPresence;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class PresenceStatusResponse {
    private final boolean onDuty;
    private final Instant lastHeartbeatAt;

    public static PresenceStatusResponse from(PharmacistPresence presence) {
        return PresenceStatusResponse.builder()
                .onDuty(presence.isOnDuty())
                .lastHeartbeatAt(presence.getLastHeartbeatAt())
                .build();
    }
}