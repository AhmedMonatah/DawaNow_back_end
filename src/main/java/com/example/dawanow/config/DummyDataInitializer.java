package com.example.dawanow.config;

import com.example.dawanow.entity.Customer;
import com.example.dawanow.entity.Pharmacist;
import com.example.dawanow.entity.Pharmacy;
import com.example.dawanow.entity.UserRole;
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
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dawanow.data.dummy.enabled", havingValue = "true")
public class DummyDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PharmacistRepository pharmacistRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Dummy data import skipped because the user table is not empty");
            return;
        }

        String hashedPassword = passwordEncoder.encode("123");

        createAdmin(hashedPassword);
        Customer customer = createCustomer(hashedPassword);
        Pharmacist pharmacist = createPharmacist("pharmacist@dawanow.com", "+201000000002", "Ahmed", "Ali", hashedPassword);
        Pharmacist pharmacist2 = createPharmacist("pharmacist2@dawanow.com", "+201000000003", "Sara", "Mohamed", hashedPassword);

        Pharmacy cairoPharmacy = createPharmacy(
                "Cairo Pharmacy", 30.0444, 31.2357,
                "Downtown Cairo", "+201111111111",
                pharmacist
        );

        pharmacist2.setPharmacy(cairoPharmacy);
        pharmacistRepository.save(pharmacist2);

        log.info("Imported {} users and {} pharmacies",
                userRepository.count(), pharmacyRepository.count());
    }

    private void createAdmin(String hashedPassword) {
        com.example.dawanow.entity.User admin = new com.example.dawanow.entity.User();
        admin.setEmail("admin@dawanow.com");
        admin.setFirstName("Admin");
        admin.setLastName("User");
        admin.setPhoneNumber("+201000000000");
        admin.setPassword(hashedPassword);
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
    }

    private Customer createCustomer(String hashedPassword) {
        Customer customer = new Customer();
        customer.setEmail("customer@dawanow.com");
        customer.setFirstName("Omar");
        customer.setLastName("Hassan");
        customer.setPhoneNumber("+201000000001");
        customer.setPassword(hashedPassword);
        customer.setRole(UserRole.CUSTOMER);
        customer.setHomeAddress("Cairo, Egypt");
        customer.setDob(LocalDate.of(1995, 6, 15));
        return userRepository.save(customer);
    }

    private Pharmacist createPharmacist(String email, String phone, String firstName, String lastName, String hashedPassword) {
        Pharmacist pharmacist = new Pharmacist();
        pharmacist.setEmail(email);
        pharmacist.setFirstName(firstName);
        pharmacist.setLastName(lastName);
        pharmacist.setPhoneNumber(phone);
        pharmacist.setPassword(hashedPassword);
        pharmacist.setRole(UserRole.PHARMACIST);
        return userRepository.save(pharmacist);
    }

    private Pharmacy createPharmacy(String name, double lat, double lng, String address,
                                    String phone, Pharmacist admin) {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setName(name);
        pharmacy.setLatitude(lat);
        pharmacy.setLongitude(lng);
        pharmacy.setAddress(address);
        pharmacy.setPhoneNumber(phone);
        pharmacy.setLicenseDocumentPath("uploads/licenses/dummy-" + name.toLowerCase().replace(" ", "-") + ".pdf");
        pharmacy.setAdminPharmacist(admin);
        Pharmacy saved = pharmacyRepository.save(pharmacy);

        admin.setPharmacy(saved);
        pharmacistRepository.save(admin);

        return saved;
    }
}
