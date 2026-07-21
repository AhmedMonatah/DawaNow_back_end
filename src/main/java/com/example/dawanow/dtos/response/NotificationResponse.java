package com.example.dawanow.dtos.response;


import com.example.dawanow.entity.notification.Notification;
import com.example.dawanow.entity.notification.NotificationRecipient;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class NotificationResponse {
    private final Long recipientId;
    private final Notification.Category category;
    private final String title;
    private final String body;
    private final Map<String, Object> dataPayload;
    private final NotificationRecipient.Status status;
    private final Instant sentAt;
    private final Instant readAt;
}