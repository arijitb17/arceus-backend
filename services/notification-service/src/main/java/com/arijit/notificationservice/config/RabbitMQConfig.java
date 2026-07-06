package com.arijit.notificationservice.config;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.otp.OtpMessagingTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Consumer side: this service owns declaring the queue and its binding,
// since it's the one actually reading from it.
@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue otpQueue() {
        return new Queue(OtpMessagingTopology.OTP_QUEUE, true);
    }

    @Bean
    public DirectExchange otpExchange() {
        return new DirectExchange(OtpMessagingTopology.OTP_EXCHANGE);
    }

    @Bean
    public Binding otpBinding(Queue otpQueue, DirectExchange otpExchange) {
        return BindingBuilder.bind(otpQueue).to(otpExchange).with(OtpMessagingTopology.OTP_ROUTING_KEY);
    }

    // Domain events bus - notification-service only cares about events it
    // can act on today (UserRegisteredEvent -> WELCOME email). Adding a new
    // "send X on Y event" rule later just means adding another binding here.
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(ArceusEventsTopology.EVENTS_EXCHANGE);
    }

    @Bean
    public Queue notificationEventsQueue() {
        return new Queue(ArceusEventsTopology.NOTIFICATION_QUEUE, true);
    }

    @Bean
    public Binding userRegisteredBinding(Queue notificationEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(eventsExchange)
                .with(ArceusEventsTopology.RK_USER_REGISTERED);
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
