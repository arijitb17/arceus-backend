package com.arijit.notificationservice.messaging;

import com.arijit.arceus.events.otp.OtpMessagingTopology;
import com.arijit.arceus.events.otp.OtpNotificationMessage;
import com.arijit.notificationservice.notification.EmailService;
import com.arijit.notificationservice.sms.PhoneOtpSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OtpConsumer {

    private final EmailService emailService;
    private final PhoneOtpSender phoneOtpSender;

    @RabbitListener(queues = OtpMessagingTopology.OTP_QUEUE)
    public void handle(OtpNotificationMessage message) {
        try {
            switch (message.channel()) {
                case EMAIL -> emailService.sendOtpEmail(message.recipient(), message.otpCode(), purposeLabel(message));
                case SMS -> phoneOtpSender.sendOtp(message.recipient());
            }
        } catch (Exception e) {
            // No retry/dead-letter queue yet - logged and dropped. Worth
            // adding once the base flow across both services is solid.
            log.error("Failed to deliver OTP via {} to {}: {}", message.channel(), message.recipient(), e.getMessage());
        }
    }

    private String purposeLabel(OtpNotificationMessage message) {
        return switch (message.purpose()) {
            case EMAIL_VERIFICATION -> "verifying your email";
            case PHONE_VERIFICATION -> "verifying your phone";
            case LOGIN_2FA -> "logging in";
            case FORGOT_PASSWORD -> "resetting your password";
            case EMAIL_CHANGE -> "confirming your new email address";
        };
    }
}
