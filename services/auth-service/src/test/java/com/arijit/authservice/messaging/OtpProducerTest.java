package com.arijit.authservice.messaging;

import com.arijit.arceus.events.otp.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OtpProducerTest {

    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks OtpProducer producer;

    @Test
    void publishesToOtpExchangeWithRoutingKey() {
        OtpNotificationMessage msg = OtpNotificationMessage.of(
                OtpChannel.EMAIL, "a@b.com", "123456", OtpPurpose.LOGIN_2FA, "Ash");

        producer.publish(msg);

        verify(rabbitTemplate).convertAndSend(
                OtpMessagingTopology.OTP_EXCHANGE, OtpMessagingTopology.OTP_ROUTING_KEY, msg);
    }
}
