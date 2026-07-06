package com.arijit.arceus.events.otp;

import java.util.UUID;

// notification-service WRITES the phone session key (after calling 2Factor's
// send endpoint); auth-service READS it back during verify. Same idea for
// the other keys - centralizing the format here means both sides can never drift.
public final class OtpRedisKeys {

    public static String emailOtp(String email, OtpPurpose purpose) {
        return "otp:email:%s:%s".formatted(purpose.name(), email);
    }

    public static String phoneSession(String phoneNumber) {
        return "otp:phone:session:%s".formatted(phoneNumber);
    }

    public static String resetToken(String token) {
        return "reset-token:%s".formatted(token);
    }

    public static String pendingEmailChange(UUID userId) {
        return "pending-email-change:%s".formatted(userId);
    }

    public static String pendingPhoneChange(UUID userId) {
        return "pending-phone-change:%s".formatted(userId);
    }

    private OtpRedisKeys() {}
}
