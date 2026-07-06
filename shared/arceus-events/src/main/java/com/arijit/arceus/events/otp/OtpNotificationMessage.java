package com.arijit.arceus.events.otp;

import java.io.Serializable;

/**
 * The envelope that travels over RabbitMQ from auth-service to
 * notification-service. otpCode is only populated for EMAIL - see
 * notification-service's PhoneOtpSender for why SMS is asymmetric
 * (2Factor generates its own code, we never have one to pass along).
 */
public record OtpNotificationMessage(
        OtpChannel channel,
        String recipient,
        String otpCode,
        OtpPurpose purpose,
        String recipientName
) implements Serializable {}
