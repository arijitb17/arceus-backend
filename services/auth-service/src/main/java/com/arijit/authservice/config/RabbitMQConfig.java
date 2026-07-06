package com.arijit.authservice.config;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.otp.OtpMessagingTopology;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Publisher side only now - notification-service and audit-service each own
// declaring their own queue and binding, since they're the ones actually
// reading from it. Declaring the exchanges here too is harmless (RabbitMQ
// exchange declaration is idempotent), and means auth-service can publish
// even if it happens to start up before its subscribers do.
@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange otpExchange() {
        return new DirectExchange(OtpMessagingTopology.OTP_EXCHANGE);
    }

    // Topic exchange for general domain events (UserRegisteredEvent,
    // LoginSuccessfulEvent, etc) - fanned out to notification-service and
    // audit-service, each binding only the routing keys it cares about.
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(ArceusEventsTopology.EVENTS_EXCHANGE);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
