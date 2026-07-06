package com.arijit.authservice.messaging;

import com.arijit.arceus.events.otp.OtpMessagingTopology;
import com.arijit.arceus.events.otp.OtpNotificationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OtpProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publish(OtpNotificationMessage message) {
        rabbitTemplate.convertAndSend(
                OtpMessagingTopology.OTP_EXCHANGE,
                OtpMessagingTopology.OTP_ROUTING_KEY,
                message
        );
    }
}
