package com.example.dawanow.service;

import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyAssignment;
import com.example.dawanow.factory.NotificationFactory;
import com.example.dawanow.repo.PharmacyAssignmentRepository;
import com.example.dawanow.repo.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentService {
    private static final double SEARCH_RADIUS_KM = 5.0;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyAssignmentRepository pharmacyAssignmentRepository;
    private final NotificationService notificationService;
    private final NotificationFactory notificationFactory;

    @Transactional
    public void assignNearbyPharmacies(MedicineRequest medicineRequest) {

        List<Pharmacy> pharmacies = pharmacyRepository.findNearbyPharmacies(medicineRequest.getDeliveryLatitude(), medicineRequest.getDeliveryLongitude(), SEARCH_RADIUS_KM);
        notificationService.sendToPharmacies(notificationFactory.orderInArea(medicineRequest), pharmacies.stream().map(Pharmacy::getId).toList());
        List<PharmacyAssignment> assignments = new ArrayList<>(pharmacies.size());

        for (Pharmacy pharmacy : pharmacies) {
            PharmacyAssignment pharmacyAssignment = new PharmacyAssignment();
            pharmacyAssignment.setPharmacy(pharmacy);
            pharmacyAssignment.setMedicineRequest(medicineRequest);
            pharmacyAssignment.setDistanceKm(calculateDistance(medicineRequest.getDeliveryLatitude(), medicineRequest.getDeliveryLongitude(), pharmacy.getLatitude(), pharmacy.getLongitude()));
            assignments.add(pharmacyAssignment);
        }
        pharmacyAssignmentRepository.saveAll(assignments); //batch save
    }


    private double calculateDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2) {

        final double EARTH_RADIUS = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2)
                        * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}

