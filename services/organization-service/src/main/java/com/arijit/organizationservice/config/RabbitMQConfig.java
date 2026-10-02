package com.arijit.organizationservice.config;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publish-only onto the platform-wide domain event bus - every
 * {@code RK_ORGANIZATION_*}/{@code RK_PROJECT_*}/{@code RK_MODULE_*}/
 * {@code RK_SECRET_*} event OrganizationEventPublisher sends. Nothing in
 * this service consumes its own events back: the {@code RK_MODULE_*}
 * events are now a request-only audit trail (ModuleEnablementService
 * resolves ACTIVE/FAILED/DISABLED synchronously via
 * ModuleProvisioningClient rather than an async result event), and this
 * service no longer tracks GitHub repo connections at all - that was a
 * read-facing mirror of an installation that only ever lived in
 * deployment-service, which isn't part of this platform.
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
