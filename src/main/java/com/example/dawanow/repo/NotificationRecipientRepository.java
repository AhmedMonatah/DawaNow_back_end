package com.example.dawanow.repo;
import com.example.dawanow.entity.notification.NotificationRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    Page<NotificationRecipient> findByPharmacistIdOrderByCreatedAtDesc(Long pharmacistId, Pageable pageable);

    Page<NotificationRecipient> findByPharmacistIdAndStatusOrderByCreatedAtDesc(
            Long pharmacistId, NotificationRecipient.Status status, Pageable pageable);

    long countByPharmacistIdAndStatusNot(Long pharmacistId, NotificationRecipient.Status status);

    Optional<NotificationRecipient> findByIdAndPharmacistId(Long id, Long pharmacistId);

    List<NotificationRecipient> findByNotificationIdAndStatus(Long notificationId, NotificationRecipient.Status status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE notification_recipient SET status = 'READ', read_at = NOW()
        WHERE pharmacist_id = :pharmacistId AND status != 'READ'
        """, nativeQuery = true)
    void markAllReadForPharmacist(@Param("pharmacistId") Long pharmacistId);

}
