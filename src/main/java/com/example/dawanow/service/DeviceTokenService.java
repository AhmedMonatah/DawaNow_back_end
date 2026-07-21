package com.example.dawanow.service;

import com.example.dawanow.dtos.request.RegisterDeviceTokenRequest;
import com.example.dawanow.entity.notification.DeviceToken;
import com.example.dawanow.repo.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Transactional
    public void register(Long pharmacistId, RegisterDeviceTokenRequest request) {
        deviceTokenRepository.findByFcmToken(request.fcmToken())
                .ifPresentOrElse(
                        token -> {
                            if (!token.getPharmacistId().equals(pharmacistId)) {
                                token.reassignTo(pharmacistId);
                            } else {
                                token.touch();
                            }
                        },
                        () -> {
                            DeviceToken token = new DeviceToken();
                            token.setPharmacistId(pharmacistId);
                            token.setFcmToken(request.fcmToken());
                            token.setPlatform(request.platform());
                            token.setDeviceId(request.deviceId());
                            token.setActive(true);
                            token.setCreatedAt(java.time.Instant.now());
                            token.setLastUsedAt(java.time.Instant.now());
                            deviceTokenRepository.save(token);
                        }
                );
    }

    @Transactional
    public void unregister(Long pharmacistId, String fcmToken) {
        deviceTokenRepository.findByFcmToken(fcmToken)
                .filter(t -> t.getPharmacistId().equals(pharmacistId))
                .ifPresent(token -> {
                    token.deactivate();
                    deviceTokenRepository.save(token);
                });
    }
}
