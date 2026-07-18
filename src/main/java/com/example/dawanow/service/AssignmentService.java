package com.example.dawanow.service;

import com.example.dawanow.entity.MedicineRequest;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.PharmacyAssignment;
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

    public void assignNearbyPharmacies(MedicineRequest medicineRequest) {
        List<PharmacyAssignment> assignments = new ArrayList<>();

        List<Pharmacy> pharmacies = pharmacyRepository.findNearbyPharmacies(medicineRequest.getDeliveryLatitude(), medicineRequest.getDeliveryLongitude(), SEARCH_RADIUS_KM);
        for (Pharmacy pharmacy : pharmacies) {
            PharmacyAssignment pharmacyAssignment = new PharmacyAssignment();
            pharmacyAssignment.setPharmacy(pharmacy);
            pharmacyAssignment.setMedicineRequest(medicineRequest);
            assignments.add(pharmacyAssignment);
        }
        pharmacyAssignmentRepository.saveAll(assignments); //batch save
    }
}

