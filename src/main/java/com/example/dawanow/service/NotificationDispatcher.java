package com.example.dawanow.service;

import com.example.dawanow.config.notification.FcmClient;
import com.example.dawanow.entity.notification.DeviceToken;
import com.example.dawanow.entity.notification.Notification;
import com.example.dawanow.entity.notification.NotificationRecipient;
import com.example.dawanow.repo.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final DeviceTokenRepository deviceTokenRepository;
    private final FcmClient fcmClient;
    private final NotificationResultHandler resultHandler;

    @Async
    public void dispatch(Notification notification, List<NotificationRecipient> recipients) {
        List<Long> pharmacistIds = recipients.stream().map(NotificationRecipient::getPharmacistId).toList();
        List<Long> recipientIds = recipients.stream().map(NotificationRecipient::getId).toList();
        List<DeviceToken> tokens = deviceTokenRepository.findByPharmacistIdInAndActiveTrue(pharmacistIds);

        if (tokens.isEmpty()) {
            resultHandler.markAllFailed(recipientIds);
            return;
        }

        Map<String, String> dataPayload = notification.getDataPayload() == null
                ? Map.of()
                : notification.getDataPayload().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));

        FcmClient.DispatchResult result = fcmClient.send(
                tokens, notification.getTitle(), notification.getBody(), dataPayload);

        resultHandler.applyResult(notification, recipientIds, result);
    }
}