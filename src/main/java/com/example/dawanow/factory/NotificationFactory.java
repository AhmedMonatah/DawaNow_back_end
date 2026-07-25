package com.example.dawanow.factory;

import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.Order;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.entity.notification.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class NotificationFactory {

    public Notification pharmacyInvitation(Pharmacy pharmacy, String inviterName) {
        return new Notification(
                Notification.Category.PHARMACY_INVITATION,
                "Pharmacy invitation",
                "%s invited you to join %s".formatted(inviterName, pharmacy.getName()),
                Map.of("pharmacyId", pharmacy.getId())
        );
    }

    public Notification orderInArea(MedicineRequest medicineRequest) {
        return new Notification(
                Notification.Category.REQUEST_IN_AREA,
                "New request nearby",
                "A new request is available in your area",
                Map.of("requestId", medicineRequest.getId())
        );
    }

    public Notification orderCreated(Order order) {
        log.info("Creating notification for order created: {}", order.getId());
        log.info("Request: {}", order.getRequest());
        return new Notification(
                Notification.Category.ORDER_CREATED,
                "NEW Order Created",
                "Your offer on request #%d was accepted".formatted(order.getRequest().getId()),
                Map.of("orderID", order.getId(), "requestId", order.getRequest().getId())
        );
    }
}