package org.auth.fullauthenticationotp.service;

import lombok.RequiredArgsConstructor;
import org.auth.fullauthenticationotp.exception.EmailSendingException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOTPEmail(String to, String otpCode) {
        try {
            org.springframework.mail.SimpleMailMessage message =
                    new org.springframework.mail.SimpleMailMessage();

            message.setTo(to);
            message.setSubject("Email Verification - OTP");
            message.setText(
                    "Your OTP for email verification is: " + otpCode + "\n\n" +
                            "This OTP will expire in 5 minutes.\n\n" +
                            "If you didn't request this, please ignore this email."
            );

            mailSender.send(message);
        } catch (Exception e) {
            throw new EmailSendingException("Failed to send OTP email", e);
        }
    }
}