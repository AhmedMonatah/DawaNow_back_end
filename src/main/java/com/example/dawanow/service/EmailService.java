package com.example.dawanow.service;

import com.example.dawanow.exception.EmailSendingException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async // Runs in the background
    public void sendOtpEmail(String toEmail, String name, String otp) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            String htmlMsg = "<h3>Welcome to DawaNow, "+name+"!</h3>"
                    + "<p>Please use the following One-Time Password (OTP) to verify your identity:</p>"
                    + "<h2 style='color:#2b6cb0; letter-spacing: 2px;'>" + otp + "</h2>"
                    + "<p>This code expires in 5 minutes.</p>";

            helper.setText(htmlMsg, true); // true sets content type to HTML
            helper.setTo(toEmail);
            helper.setSubject("DawaNow - Verify Your Account");
            helper.setFrom(from);

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new EmailSendingException("Failed to send email verification code");
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String name, String otp) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            String htmlMsg = "<h3>Hello " + name + ",</h3>"
                    + "<p>We received a request to reset your DawaNow password. Use the code below:</p>"
                    + "<h2 style='color:#2b6cb0; letter-spacing: 2px;'>" + otp + "</h2>"
                    + "<p>This code expires in 5 minutes. If you didn't request this, you can ignore this email.</p>";

            helper.setText(htmlMsg, true);
            helper.setTo(toEmail);
            helper.setSubject("DawaNow - Password Reset");
            helper.setFrom(from);

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new EmailSendingException("Failed to send password reset email");
        }
    }
}