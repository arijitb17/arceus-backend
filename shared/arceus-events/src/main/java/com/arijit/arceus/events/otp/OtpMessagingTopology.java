package com.arijit.arceus.events.otp;


public final class OtpMessagingTopology {
    public static final String OTP_QUEUE = "otp.notifications.queue";
    public static final String OTP_EXCHANGE = "otp.notifications.exchange";
    public static final String OTP_ROUTING_KEY = "otp.notifications.send";

    private OtpMessagingTopology() {}
}
