package com.arijit.featureflagservice.config;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * feature-flag-service only publishes onto the platform-wide domain event
 * bus ({@code arceus.events.exchange}) - {@code flag.created}, {@code
 * flag.updated}, {@code flag.enabled}, {@code flag.disabled}, {@code
 * flag.deleted} (see FlagEventPublisher). It never consumes this exchange,
 * only declares it (so it exists even if this is the first service up)
 * and publishes to it. Unlike scheduler-service, this service has no
 * internal queues of its own today - scheduled activation/deactivation is
 * delegated entirely to scheduler-service rather than reimplemented here
 * (see FlagSchedulingService), so there's no delay/retry queue topology
 * to declare.
 */
@Configuration
public class RabbitMQConfig {

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
