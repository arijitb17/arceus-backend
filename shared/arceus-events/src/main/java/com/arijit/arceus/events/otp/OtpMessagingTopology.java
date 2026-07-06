package com.arijit.arceus.events.otp;

// The queue/exchange/routing-key names, in one place both services import,
// so a typo in one service can't silently create a second queue nobody reads.
public final class OtpMessagingTopology {
    public static final String OTP_QUEUE = "otp.notifications.queue";
    public static final String OTP_EXCHANGE = "otp.notifications.exchange";
    public static final String OTP_ROUTING_KEY = "otp.notifications.send";

    private OtpMessagingTopology() {}
}
