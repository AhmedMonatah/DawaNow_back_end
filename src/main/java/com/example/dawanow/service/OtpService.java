package com.example.dawanow.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    // Thread-safe map to store: Email -> OtpData
    private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private static final int EXPIRY_MINUTES = 5;

    // A simple helper record to store the code and when it dies
    private record OtpData(String code, LocalDateTime expiryTime) {}

    // Generate and store OTP in the map
    public String generateOtp(String email) {
        // Generate a secure 6-digit code
        String otpCode = String.valueOf(100000 + secureRandom.nextInt(900000));
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(EXPIRY_MINUTES);

        // Store it (automatically overwrites any old OTP for this email)
        otpStorage.put(email, new OtpData(otpCode, expiryTime));

        return otpCode;
    }

    // Validate OTP
    public boolean validateOtp(String email, String inputCode) {
        OtpData otpData = otpStorage.get(email);

        if (otpData == null) {
            return false; // No OTP was generated for this email
        }

        if (!otpData.code().equals(inputCode))
            return false;

        if (otpData.expiryTime().isBefore(LocalDateTime.now())) {
            otpStorage.remove(email);
            throw new IllegalStateException("OTP has expired");
        }

        otpStorage.remove(email);

        return true;
    }

    @Scheduled(fixedRate = 60000)
    public void removeExpiredOtps() {
        LocalDateTime now = LocalDateTime.now();
        otpStorage.entrySet().removeIf(entry ->
                entry.getValue()
                        .expiryTime()
                        .isBefore(now));
    }
}
