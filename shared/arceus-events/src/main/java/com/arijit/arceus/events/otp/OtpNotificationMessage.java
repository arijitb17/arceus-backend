package com.arijit.arceus.events.otp;

import java.io.Serializable;
import java.util.UUID;

public record OtpNotificationMessage(
        String messageId,
        OtpChannel channel,
        String recipient,
        String otpCode,
        OtpPurpose purpose,
        String recipientName
) implements Serializable {
    public static OtpNotificationMessage of(OtpChannel channel, String recipient, String otpCode,
                                             OtpPurpose purpose, String recipientName) {
        return new OtpNotificationMessage(UUID.randomUUID().toString(), channel, recipient, otpCode, purpose, recipientName);
    }
}
