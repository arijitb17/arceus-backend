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
