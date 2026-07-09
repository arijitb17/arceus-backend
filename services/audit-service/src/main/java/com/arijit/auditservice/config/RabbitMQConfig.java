package com.arijit.auditservice.config;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * audit-service is the one subscriber that wants everything - "#" matches
 * every routing key published to the events exchange, present and future,
 * so a new event type never needs a matching binding added here.
 * <p>
 * Every queue here also gets a dead-letter routing: after
 * spring.rabbitmq.listener.simple.retry's attempts are exhausted (see
 * application.yml), a message is rejected without requeue and RabbitMQ
 * routes it to the matching {@code .dlq} queue instead of looping forever
 * or being dropped - see DomainEventAuditConsumer's javadoc for the other
 * half of this (why most failures now propagate instead of being swallowed).
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(ArceusEventsTopology.EVENTS_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(ArceusEventsTopology.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue auditEventsQueue() {
        return QueueBuilder.durable(ArceusEventsTopology.AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", ArceusEventsTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ArceusEventsTopology.AUDIT_QUEUE)
                .build();
    }

    @Bean
    public Queue auditEventsDeadLetterQueue() {
        return new Queue(ArceusEventsTopology.AUDIT_QUEUE + ArceusEventsTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding auditEventsDeadLetterBinding(Queue auditEventsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(auditEventsDeadLetterQueue).to(deadLetterExchange).with(ArceusEventsTopology.AUDIT_QUEUE);
    }

    @Bean
    public Binding auditAllEventsBinding(Queue auditEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(auditEventsQueue).to(eventsExchange).with(ArceusEventsTopology.ROUTING_KEY_ALL);
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
