package com.example.dawanow.service;

import com.example.dawanow.dtos.response.NotificationResponse;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.notification.Notification;
import com.example.dawanow.entity.notification.NotificationRecipient;
import com.example.dawanow.mapper.NotificationMapper;
import com.example.dawanow.repo.NotificationRecipientRepository;
import com.example.dawanow.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationDispatcher notificationDispatcher;
    private final PharmacistService pharmacistService;

    public Page<NotificationResponse> list(Long pharmacistId, NotificationRecipient.Status status, Pageable pageable) {
        Page<NotificationRecipient> page = status == null
                ? recipientRepository.findByPharmacistIdOrderByCreatedAtDesc(pharmacistId, pageable)
                : recipientRepository.findByPharmacistIdAndStatusOrderByCreatedAtDesc(pharmacistId, status, pageable);
        return page.map(notificationMapper::toResponse);
    }

    public long countUnread(Long pharmacistId) {
        return recipientRepository.countByPharmacistIdAndStatusNot(pharmacistId, NotificationRecipient.Status.READ);
    }

    @Transactional
    public void markRead(Long pharmacistId, Long recipientId) {
        recipientRepository.findByIdAndPharmacistId(recipientId, pharmacistId)
                .ifPresent(NotificationRecipient::markRead);
    }

    @Transactional
    public void markAllRead(Long pharmacistId) {
        recipientRepository.markAllReadForPharmacist(pharmacistId);
    }

    @Transactional
    public void sendToPharmacists(Notification notification, List<Long> recipientPharmacistIds) {
        if (recipientPharmacistIds.isEmpty()) {
            return;
        }
        notificationRepository.save(notification);

        List<NotificationRecipient> recipients = recipientPharmacistIds.stream()
                .map(pharmacistId -> new NotificationRecipient(notification, pharmacistId))
                .toList();
        recipientRepository.saveAll(recipients);

        notificationDispatcher.dispatch(notification, recipients);
    }

    @Transactional
    public void sendToPharmacies(Notification notification, List<Long> pharmacyIds) {
        Map<Long, List<Pharmacist>> activeByPharmacy = pharmacistService.findActivePharmacistsByPharmaciesId(pharmacyIds);

        List<Long> pharmacistIds = activeByPharmacy.values().stream()
                .flatMap(List::stream)
                .map(Pharmacist::getId)
                .distinct()
                .toList();

        sendToPharmacists(notification, pharmacistIds);
    }
}
