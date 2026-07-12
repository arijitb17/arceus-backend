package com.arijit.gatewayservice.live;

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
 * This is the "dedicated fan-out layer" option the backend-gaps doc
 * flagged for live/streaming updates, rather than a WebSocket server per
 * service: the Gateway already sits in front of every client, and every
 * domain event already flows onto one shared bus (see ArceusEventsTopology)
 * that audit-service already binds a catch-all queue to for its trail - so
 * the Gateway binding its own catch-all queue and re-emitting each event as
 * an SSE message is new wiring, not new event-publishing infrastructure in
 * every downstream service.
 */
@Configuration
public class LiveUpdatesRabbitConfig {

    public static final String QUEUE = "arceus.gateway.live.events.queue";

    @Bean
    public DirectExchange liveUpdatesDeadLetterExchange() {
        return new DirectExchange(ArceusEventsTopology.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public TopicExchange liveUpdatesEventsExchange() {
        return new TopicExchange(ArceusEventsTopology.EVENTS_EXCHANGE);
    }

    @Bean
    public Queue liveUpdatesQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", ArceusEventsTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", QUEUE)
                .build();
    }

    @Bean
    public Queue liveUpdatesDeadLetterQueue() {
        return new Queue(QUEUE + ArceusEventsTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding liveUpdatesDeadLetterBinding(Queue liveUpdatesDeadLetterQueue, DirectExchange liveUpdatesDeadLetterExchange) {
        return BindingBuilder.bind(liveUpdatesDeadLetterQueue).to(liveUpdatesDeadLetterExchange).with(QUEUE);
    }

    // Catch-all, same as audit-service's binding: the Gateway doesn't know
    // (and shouldn't need to know) which event types matter to whichever
    // dashboard subscribes next - filtering by type is a client-side
    // concern (see LiveUpdatesController's ?topics= param).
    @Bean
    public Binding liveUpdatesBinding(Queue liveUpdatesQueue, TopicExchange liveUpdatesEventsExchange) {
        return BindingBuilder.bind(liveUpdatesQueue)
                .to(liveUpdatesEventsExchange)
                .with(ArceusEventsTopology.ROUTING_KEY_ALL);
    }

    @Bean
    public MessageConverter liveUpdatesJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter liveUpdatesJsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(liveUpdatesJsonMessageConverter);
        return template;
    }
}
