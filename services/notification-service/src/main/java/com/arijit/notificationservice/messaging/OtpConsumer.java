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
    private final IdempotencyGuard idempotencyGuard;

    @RabbitListener(queues = OtpMessagingTopology.OTP_QUEUE)
    public void handle(OtpNotificationMessage message) {
        if (idempotencyGuard.alreadyProcessed(message.messageId())) {
            log.info("Skipping redelivered OTP message {} ({} to {}) - already sent",
                    message.messageId(), message.channel(), message.recipient());
            return;
        }

        // Deliberately NOT catching a broad Exception here anymore: a
        // transient failure (SMTP/SMS provider down) should be retried
        // with backoff (spring.rabbitmq.listener.simple.retry) and, once
        // exhausted, dead-lettered - not silently logged and dropped,
        // which used to mean the user's OTP just never arrived with no
        // way to notice.
        switch (message.channel()) {
            case EMAIL -> emailService.sendOtpEmail(message.recipient(), message.otpCode(), purposeLabel(message));
            case SMS -> phoneOtpSender.sendOtp(message.recipient());
        }
        idempotencyGuard.markProcessed(message.messageId());
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
