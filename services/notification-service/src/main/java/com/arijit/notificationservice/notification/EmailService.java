package com.arijit.notificationservice.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp, String purposeLabel) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your Arceus Platform verification code");
        message.setText("""
                Your one-time code for %s is: %s

                This code expires in a few minutes. If you didn't request this, you can ignore this email.
                """.formatted(purposeLabel, otp));
        mailSender.send(message);
    }

    /** Used by the generic /notifications pipeline - subject/body already rendered by TemplateRenderer. */
    public void sendRenderedEmail(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject != null ? subject : "Notification from Arceus Platform");
        message.setText(body);
        mailSender.send(message);
    }
}
