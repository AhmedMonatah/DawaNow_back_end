package com.example.dawanow.repo;



import com.example.dawanow.entity.notification.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByFcmToken(String fcmToken);

    Optional<DeviceToken> findByPharmacistIdAndDeviceId(Long pharmacistId, String deviceId);

    List<DeviceToken> findByPharmacistIdInAndActiveTrue(List<Long> pharmacistIds);

    @Modifying
    @Query("UPDATE DeviceToken d SET d.active = false WHERE d.id IN :ids")
    void deactivateAll(@Param("ids") List<Long> ids);
}