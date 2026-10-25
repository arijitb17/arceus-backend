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

@Configuration
public class RabbitMQConfig {
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(ArceusEventsTopology.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue otpQueue() {
        return QueueBuilder.durable(OtpMessagingTopology.OTP_QUEUE)
                .withArgument("x-dead-letter-exchange", ArceusEventsTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", OtpMessagingTopology.OTP_QUEUE)
                .build();
    }

    @Bean
    public Queue otpDeadLetterQueue() {
        return new Queue(OtpMessagingTopology.OTP_QUEUE + ArceusEventsTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding otpDeadLetterBinding(Queue otpDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(otpDeadLetterQueue).to(deadLetterExchange).with(OtpMessagingTopology.OTP_QUEUE);
    }

    @Bean
    public DirectExchange otpExchange() {
        return new DirectExchange(OtpMessagingTopology.OTP_EXCHANGE);
    }

    @Bean
    public Binding otpBinding(Queue otpQueue, DirectExchange otpExchange) {
        return BindingBuilder.bind(otpQueue).to(otpExchange).with(OtpMessagingTopology.OTP_ROUTING_KEY);
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(ArceusEventsTopology.EVENTS_EXCHANGE);
    }

    @Bean
    public Queue notificationEventsQueue() {
        return QueueBuilder.durable(ArceusEventsTopology.NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", ArceusEventsTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ArceusEventsTopology.NOTIFICATION_QUEUE)
                .build();
    }

    @Bean
    public Queue notificationEventsDeadLetterQueue() {
        return new Queue(ArceusEventsTopology.NOTIFICATION_QUEUE + ArceusEventsTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding notificationEventsDeadLetterBinding(Queue notificationEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationEventsDeadLetterQueue).to(deadLetterExchange).with(ArceusEventsTopology.NOTIFICATION_QUEUE);
    }

    @Bean
    public Binding userRegisteredBinding(Queue notificationEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(eventsExchange)
                .with(ArceusEventsTopology.RK_USER_REGISTERED);
    }

    @Bean
    public Binding deploymentCompletedBinding(Queue notificationEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(eventsExchange)
                .with(ArceusEventsTopology.RK_DEPLOYMENT_COMPLETED);
    }

    @Bean
    public Binding deploymentFailedBinding(Queue notificationEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(eventsExchange)
                .with(ArceusEventsTopology.RK_DEPLOYMENT_FAILED);
    }

    @Bean
    public Binding deploymentRolledBackBinding(Queue notificationEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(eventsExchange)
                .with(ArceusEventsTopology.RK_DEPLOYMENT_ROLLED_BACK);
    }

    @Bean
    public Binding deploymentCancelledBinding(Queue notificationEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationEventsQueue)
                .to(eventsExchange)
                .with(ArceusEventsTopology.RK_DEPLOYMENT_CANCELLED);
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
