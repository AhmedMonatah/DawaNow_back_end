package com.example.dawanow.factory;

import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyOffer;
import com.example.dawanow.entity.notification.Notification;
import org.springframework.stereotype.Component;

import java.util.Map;

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

    public Notification offerAccepted(PharmacyOffer offer) {
        return new Notification(
                Notification.Category.OFFER_ACCEPTED,
                "Offer accepted",
                "Your offer on request #%d was accepted".formatted(offer.getRequest().getId()),
                Map.of("offerId", offer.getId(), "requestId", offer.getRequest().getId())
        );
    }
}