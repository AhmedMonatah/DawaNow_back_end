package com.example.dawanow.mapper;

import com.example.dawanow.dtos.response.NotificationResponse;
import com.example.dawanow.entity.notification.NotificationRecipient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "recipientId", source = "id")
    @Mapping(target = "category", source = "notification.category")
    @Mapping(target = "title", source = "notification.title")
    @Mapping(target = "body", source = "notification.body")
    @Mapping(target = "dataPayload", source = "notification.dataPayload")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "sentAt", source = "sentAt")
    @Mapping(target = "readAt", source = "readAt")
    NotificationResponse toResponse(NotificationRecipient recipient);
}
