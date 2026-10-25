package com.arijit.schedulerservice.config;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.scheduler.SchedulerMessagingTopology;
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
    public TopicExchange eventsExchange() {
        return new TopicExchange(ArceusEventsTopology.EVENTS_EXCHANGE);
    }

    @Bean
    public DirectExchange schedulerInternalExchange() {
        return new DirectExchange(SchedulerMessagingTopology.INTERNAL_EXCHANGE);
    }

    @Bean
    public DirectExchange schedulerDeadLetterExchange() {
        return new DirectExchange(SchedulerMessagingTopology.DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue retryRequestQueue() {
        return QueueBuilder.durable(SchedulerMessagingTopology.RETRY_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", SchedulerMessagingTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SchedulerMessagingTopology.RETRY_REQUEST_QUEUE)
                .build();
    }

    @Bean
    public Queue retryRequestDeadLetterQueue() {
        return new Queue(SchedulerMessagingTopology.RETRY_REQUEST_QUEUE + SchedulerMessagingTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding retryRequestDeadLetterBinding(Queue retryRequestDeadLetterQueue, DirectExchange schedulerDeadLetterExchange) {
        return BindingBuilder.bind(retryRequestDeadLetterQueue).to(schedulerDeadLetterExchange)
                .with(SchedulerMessagingTopology.RETRY_REQUEST_QUEUE);
    }

    @Bean
    public Binding retryRequestBinding(Queue retryRequestQueue, DirectExchange schedulerInternalExchange) {
        return BindingBuilder.bind(retryRequestQueue).to(schedulerInternalExchange)
                .with(SchedulerMessagingTopology.RETRY_REQUEST_QUEUE);
    }

    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(SchedulerMessagingTopology.DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", SchedulerMessagingTopology.INTERNAL_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SchedulerMessagingTopology.DELAY_READY_QUEUE)
                .build();
    }

    @Bean
    public Binding delayQueueBinding(Queue delayQueue, DirectExchange schedulerInternalExchange) {
        return BindingBuilder.bind(delayQueue).to(schedulerInternalExchange)
                .with(SchedulerMessagingTopology.DELAY_QUEUE);
    }

    @Bean
    public Queue delayReadyQueue() {
        return QueueBuilder.durable(SchedulerMessagingTopology.DELAY_READY_QUEUE)
                .withArgument("x-dead-letter-exchange", SchedulerMessagingTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", SchedulerMessagingTopology.DELAY_READY_QUEUE)
                .build();
    }

    @Bean
    public Queue delayReadyDeadLetterQueue() {
        return new Queue(SchedulerMessagingTopology.DELAY_READY_QUEUE + SchedulerMessagingTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding delayReadyDeadLetterBinding(Queue delayReadyDeadLetterQueue, DirectExchange schedulerDeadLetterExchange) {
        return BindingBuilder.bind(delayReadyDeadLetterQueue).to(schedulerDeadLetterExchange)
                .with(SchedulerMessagingTopology.DELAY_READY_QUEUE);
    }

    @Bean
    public Binding delayReadyBinding(Queue delayReadyQueue, DirectExchange schedulerInternalExchange) {
        return BindingBuilder.bind(delayReadyQueue).to(schedulerInternalExchange)
                .with(SchedulerMessagingTopology.DELAY_READY_QUEUE);
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
