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
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(ArceusEventsTopology.EVENTS_EXCHANGE);
    }

    @Bean
    public Queue auditEventsQueue() {
        return new Queue(ArceusEventsTopology.AUDIT_QUEUE, true);
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
