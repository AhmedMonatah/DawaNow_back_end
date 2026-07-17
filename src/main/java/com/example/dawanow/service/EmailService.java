package com.example.dawanow.service;

import com.example.dawanow.exception.EmailSendingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Value("${resend.api.key:re_hvMkTbEG_4XThGrgHBi52s6b6ae3iQsEo}")
    private String resendApiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String fromEmail;

    public void sendOtpEmail(String toEmail, String name, String otp) {
        try {
            logger.info("Attempting to send OTP email to: {} via Resend API", toEmail);
            
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey.trim());

            String htmlMsg = "<h3>Welcome to DawaNow, " + name + "!</h3>"
                    + "<p>Please use the following One-Time Password (OTP) to verify your identity:</p>"
                    + "<h2 style='color:#2b6cb0; letter-spacing: 2px;'>" + otp + "</h2>"
                    + "<p>This code expires in 5 minutes.</p>";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("from", "DawaNow <" + fromEmail + ">");
            requestBody.put("to", List.of(toEmail));
            requestBody.put("subject", "DawaNow - Verify Your Account");
            requestBody.put("html", htmlMsg);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.resend.com/emails", 
                    request, 
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("OTP email sent successfully via Resend API to: {}", toEmail);
            } else {
                logger.error("Failed to send email via Resend API. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                throw new EmailSendingException("Failed to send email verification code");
            }
        } catch (Exception e) {
            logger.error("Unexpected error sending OTP email via Resend to: {}. Error: {}", toEmail, e.getMessage(), e);
            throw new EmailSendingException("Failed to send email verification code: " + e.getMessage());
        }
    }
}