package com.example.dawanow.service;

import com.example.dawanow.exception.EmailSendingException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(String toEmail, String name, String otp) {
        try {
            logger.info("Attempting to send OTP email to: {}", toEmail);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            String htmlMsg = "<h3>Welcome to DawaNow, " + name + "!</h3>"
                    + "<p>Please use the following One-Time Password (OTP) to verify your identity:</p>"
                    + "<h2 style='color:#2b6cb0; letter-spacing: 2px;'>" + otp + "</h2>"
                    + "<p>This code expires in 5 minutes.</p>";

            helper.setText(htmlMsg, true);
            helper.setTo(toEmail);
            helper.setSubject("DawaNow - Verify Your Account");
            helper.setFrom(from);

            mailSender.send(mimeMessage);
            logger.info("OTP email sent successfully to: {}", toEmail);
        } catch (MessagingException e) {
            logger.error("Failed to send OTP email to: {}. Error: {}", toEmail, e.getMessage(), e);
            throw new EmailSendingException("Failed to send email verification code: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error sending OTP email to: {}. Error: {}", toEmail, e.getMessage(), e);
            throw new EmailSendingException("Failed to send email verification code: " + e.getMessage());
        }
    }
}