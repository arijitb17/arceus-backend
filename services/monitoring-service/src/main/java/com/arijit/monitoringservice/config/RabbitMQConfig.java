package com.arijit.monitoringservice.config;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * monitoring-service publishes onto the platform-wide domain event bus
 * ({@code arceus.events.exchange}) - {@code service.down}, {@code
 * service.recovered}, {@code alert.triggered}, {@code alert.resolved} (see
 * MonitoringEventPublisher) - and, as of the usage-rollup work, also
 * consumes from it: gateway-service's {@code usage.request_recorded}
 * events, aggregated into {@code UsageRollup} rows by UsageRollupConsumer.
 * This is the one exception to "monitoring-service never consumes this
 * exchange" from the original version of this class's javadoc.
 */
@Configuration
public class RabbitMQConfig {

    private static final String USAGE_QUEUE = "arceus.monitoring.usage-rollup.queue";

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

    @Bean
    public DirectExchange monitoringDeadLetterExchange() {
        return new DirectExchange(ArceusEventsTopology.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue usageRollupQueue() {
        return QueueBuilder.durable(USAGE_QUEUE)
                .withArgument("x-dead-letter-exchange", ArceusEventsTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", USAGE_QUEUE)
                .build();
    }

    @Bean
    public Queue usageRollupDeadLetterQueue() {
        return new Queue(USAGE_QUEUE + ArceusEventsTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding usageRollupDeadLetterBinding(Queue usageRollupDeadLetterQueue, DirectExchange monitoringDeadLetterExchange) {
        return BindingBuilder.bind(usageRollupDeadLetterQueue).to(monitoringDeadLetterExchange).with(USAGE_QUEUE);
    }

    @Bean
    public Binding usageRollupBinding(Queue usageRollupQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(usageRollupQueue).to(eventsExchange).with(ArceusEventsTopology.RK_USAGE_REQUEST_RECORDED);
    }
}
