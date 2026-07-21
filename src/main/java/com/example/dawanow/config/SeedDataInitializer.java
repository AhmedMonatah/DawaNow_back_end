package com.example.dawanow.config;

import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.PharmacistPresence;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.User;
import com.example.dawanow.entity.UserRole;
import com.example.dawanow.repo.PharmacistPresenceRepository;
import com.example.dawanow.repo.PharmacistRepository;
import com.example.dawanow.repo.PharmacyRepository;
import com.example.dawanow.repo.UserRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "dawanow.data.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SeedDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PharmacistRepository pharmacistRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacistPresenceRepository pharmacistPresenceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String hashedPassword = passwordEncoder.encode("123");

        createCustomers(hashedPassword);

        // 4 pharmacies clustered in Heliopolis area (all within ~1.2km of each other)
        createPharmacyWithPharmacists(
                "Heliopolis Pharmacy", 30.0860, 31.3260,
                "Heliopolis, Cairo", "+201111111111",
                hashedPassword, 0
        );
        createPharmacyWithPharmacists(
                "Nasr City Pharmacy", 30.0940, 31.3340,
                "Nasr City, Cairo", "+201222222222",
                hashedPassword, 3
        );
        createPharmacyWithPharmacists(
                "Nozha Pharmacy", 30.0860, 31.3340,
                "Nozha, Cairo", "+201333333333",
                hashedPassword, 6
        );
        createPharmacyWithPharmacists(
                "Sheraton Pharmacy", 30.0940, 31.3260,
                "Sheraton, Cairo", "+201444444444",
                hashedPassword, 9
        );
        // 5th pharmacy far away in Alexandria
        createPharmacyWithPharmacists(
                "Alexandria Pharmacy", 31.2001, 29.9187,
                "Alexandria Corniche", "+201555555555",
                hashedPassword, 12
        );

        log.info("Seed data import complete: {} users, {} pharmacies",
                userRepository.count(), pharmacyRepository.count());
    }

    private void createCustomers(String hashedPassword) {
        Object[][] customers = {
                {"customer1@dawanow.com", "Ahmed", "Khaled", "+201000000011", "Cairo, Egypt", "1990-03-10"},
                {"customer2@dawanow.com", "Mona", "Youssef", "+201000000012", "Alexandria, Egypt", "1992-07-22"},
                {"customer3@dawanow.com", "Karim", "Hassan", "+201000000013", "Giza, Egypt", "1988-11-05"},
                {"customer4@dawanow.com", "Nadia", "Fathy", "+201000000014", "Maadi, Cairo", "1995-01-18"},
                {"customer5@dawanow.com", "Tamer", "Mostafa", "+201000000015", "Nasr City, Cairo", "1993-09-30"},
        };

        for (Object[] c : customers) {
            String email = (String) c[0];
            if (userRepository.findByEmail(email) != null) {
                log.info("Customer {} already exists, skipping", email);
                continue;
            }
            Customer customer = new Customer();
            customer.setEmail(email);
            customer.setFirstName((String) c[1]);
            customer.setLastName((String) c[2]);
            customer.setPhoneNumber((String) c[3]);
            customer.setPassword(hashedPassword);
            customer.setRole(UserRole.CUSTOMER);
            customer.setHomeAddress((String) c[4]);
            customer.setDob(LocalDate.parse((String) c[5]));
            userRepository.save(customer);
        }
    }

    private void createPharmacyWithPharmacists(String name, double lat, double lng,
                                                String address, String phone,
                                                String hashedPassword, int baseIndex) {
        String[][] allPharmacists = {
                {"pharmacist1@dawanow.com",  "+201010000001", "Ahmed",   "Khaled"},
                {"pharmacist2@dawanow.com",  "+201010000002", "Mona",    "Youssef"},
                {"pharmacist3@dawanow.com",  "+201010000003", "Karim",   "Hassan"},
                {"pharmacist4@dawanow.com",  "+201010000004", "Nadia",   "Fathy"},
                {"pharmacist5@dawanow.com",  "+201010000005", "Tamer",   "Mostafa"},
                {"pharmacist6@dawanow.com",  "+201010000006", "Yasser",  "Ali"},
                {"pharmacist7@dawanow.com",  "+201010000007", "Heba",    "Mansour"},
                {"pharmacist8@dawanow.com",  "+201010000008", "Sherif",  "Gamal"},
                {"pharmacist9@dawanow.com",  "+201010000009", "Laila",   "Ibrahim"},
                {"pharmacist10@dawanow.com", "+201010000010", "Hassan",  "Omar"},
                {"pharmacist11@dawanow.com", "+201010000011", "Fatima",  "Adel"},
                {"pharmacist12@dawanow.com", "+201010000012", "Mahmoud", "Samir"},
                {"pharmacist13@dawanow.com", "+201010000013", "Nour",    "Eldin"},
                {"pharmacist14@dawanow.com", "+201010000014", "Sarah",   "Ahmed"},
                {"pharmacist15@dawanow.com", "+201010000015", "Ayman",   "Mohamed"},
        };

        Pharmacist[] pharmacists = new Pharmacist[3];
        for (int i = 0; i < 3; i++) {
            String[] data = allPharmacists[baseIndex + i];
            String email = data[0];
            String rawPhone = data[1];
            User existing = userRepository.findByEmail(email);
            if (existing != null) {
                log.info("Pharmacist {} already exists (email), reusing", email);
                pharmacists[i] = (Pharmacist) existing;
                ensurePresence(pharmacists[i], true);
                continue;
            }
            User existingByPhone = userRepository.findByPhoneNumber(rawPhone);
            if (existingByPhone != null) {
                log.info("Pharmacist with phone {} already exists, reusing", rawPhone);
                pharmacists[i] = (Pharmacist) existingByPhone;
                ensurePresence(pharmacists[i], true);
                continue;
            }
            Pharmacist p = new Pharmacist();
            p.setEmail(email);
            p.setFirstName(data[2]);
            p.setLastName(data[3]);
            p.setPhoneNumber(rawPhone);
            p.setPassword(hashedPassword);
            p.setRole(UserRole.PHARMACIST);
            pharmacists[i] = userRepository.save(p);
            setPresence(pharmacists[i], true);
        }

        if (pharmacyRepository.existsByAdminPharmacistId(pharmacists[0].getId())) {
            log.info("Pharmacy with admin {} already exists, skipping", pharmacists[0].getId());
            return;
        }

        if (pharmacyRepository.findByName(name).isPresent()) {
            log.info("Pharmacy {} already exists, skipping", name);
            return;
        }

        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setName(name);
        pharmacy.setLatitude(lat);
        pharmacy.setLongitude(lng);
        pharmacy.setAddress(address);
        pharmacy.setPhoneNumber(phone);
        pharmacy.setLicenseDocumentPath("uploads/licenses/seed-" + name.toLowerCase().replace(" ", "-") + ".pdf");
        pharmacy.setAdminPharmacist(pharmacists[0]);
        Pharmacy saved = pharmacyRepository.save(pharmacy);

        for (Pharmacist p : pharmacists) {
            p.setPharmacy(saved);
            pharmacistRepository.save(p);
        }
    }

    private void setPresence(Pharmacist pharmacist, boolean onDuty) {
        PharmacistPresence presence = new PharmacistPresence(pharmacist.getId());
        if (onDuty) {
            presence.goOnDuty();
        }
        pharmacistPresenceRepository.save(presence);
    }

    private void ensurePresence(Pharmacist pharmacist, boolean onDuty) {
        if (pharmacistPresenceRepository.findByPharmacistId(pharmacist.getId()).isEmpty()) {
            setPresence(pharmacist, onDuty);
        }
    }
}
