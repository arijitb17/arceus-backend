package com.arijit.monitoringservice.config;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * monitoring-service only publishes onto the platform-wide domain event bus
 * ({@code arceus.events.exchange}) - {@code service.down}, {@code
 * service.recovered}, {@code alert.triggered}, {@code alert.resolved} (see
 * MonitoringEventPublisher). It never consumes this exchange, only declares
 * it (so it exists even if this happens to be the first service up) and
 * publishes to it, same pattern as feature-flag-service.
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
