package com.example.dawanow.service;

import com.example.dawanow.config.notification.FcmClient;
import com.example.dawanow.entity.notification.DeviceToken;
import com.example.dawanow.entity.notification.Notification;
import com.example.dawanow.entity.notification.NotificationRecipient;
import com.example.dawanow.repo.DeviceTokenRepository;
import com.example.dawanow.repo.NotificationRecipientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class NotificationResultHandler {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRecipientRepository recipientRepository;

    @Transactional
    void applyResult(Notification notification, List<Long> recipientIds, FcmClient.DispatchResult result) {
        List<NotificationRecipient> recipients = recipientRepository.findAllById(recipientIds);

        if (!result.deadTokens.isEmpty()) {
            deviceTokenRepository.deactivateAll(result.deadTokens.stream().map(DeviceToken::getId).toList());
        }

        Map<Long, NotificationRecipient> byPharmacistId = recipients.stream()
                .collect(Collectors.toMap(NotificationRecipient::getPharmacistId, r -> r));

        result.succeeded.forEach(token -> {
            NotificationRecipient recipient = byPharmacistId.get(token.getPharmacistId());
            if (recipient != null && recipient.getStatus() == NotificationRecipient.Status.PENDING) {
                recipient.markSent();
            }
        });

        recipients.stream()
                .filter(r -> r.getStatus() == NotificationRecipient.Status.PENDING)
                .forEach(NotificationRecipient::markFailed);

        recipientRepository.saveAll(recipients);
    }

    @Transactional
    void markAllFailed(List<Long> recipientIds) {
        List<NotificationRecipient> recipients = recipientRepository.findAllById(recipientIds);
        recipients.stream()
                .filter(r -> r.getStatus() == NotificationRecipient.Status.PENDING)
                .forEach(NotificationRecipient::markFailed);
        recipientRepository.saveAll(recipients);
    }
}