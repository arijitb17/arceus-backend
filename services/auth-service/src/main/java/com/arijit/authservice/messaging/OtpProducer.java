package com.arijit.authservice.messaging;

import com.arijit.arceus.events.otp.OtpMessagingTopology;
import com.arijit.arceus.events.otp.OtpNotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OtpProducer {
    private final RabbitTemplate rabbitTemplate;

    public void publish(OtpNotificationMessage message) {
        log.debug("Publishing OTP message: channel={} purpose={}", message.channel(), message.purpose());
        rabbitTemplate.convertAndSend(
                OtpMessagingTopology.OTP_EXCHANGE,
                OtpMessagingTopology.OTP_ROUTING_KEY,
                message
        );
    }
}
