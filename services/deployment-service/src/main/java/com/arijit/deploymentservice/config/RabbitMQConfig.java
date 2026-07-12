package com.arijit.deploymentservice.config;

import com.arijit.arceus.events.deployment.DeploymentMessagingTopology;
import com.arijit.arceus.events.domain.ArceusEventsTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Three independent messaging concerns live here:
 * <ol>
 *   <li><b>Publish-only</b> to the platform-wide domain event bus
 *   ({@code arceus.events.exchange}) - {@code deployment.created}, {@code
 *   deployment.started}, {@code deployment.completed}, {@code
 *   deployment.failed}, {@code deployment.rolledback}, {@code
 *   deployment.cancelled} - see DeploymentEventPublisher.</li>
 *   <li><b>Consume</b> from {@code arceus.events.exchange} on
 *   deployment-service's own queue, bound only to monitoring-service's
 *   health-transition routing keys - see MonitoringHealthEventsConsumer.
 *   This is a second, independent binding onto the *same* shared exchange
 *   declared above, not a second exchange.</li>
 *   <li><b>Consume</b> from deployment-service's own internal exchange -
 *   {@code APPROVAL_DECISIONS_QUEUE}, fed by an external approval source
 *   (see ApprovalEventsConsumer).</li>
 * </ol>
 * Every queue here dead-letters into its own scheme's DLX after
 * spring.rabbitmq.listener.simple.retry's attempts are exhausted (see
 * application.yml), same pattern as every other service in the platform.
 */
@Configuration
public class RabbitMQConfig {

    // ---------- Platform domain event bus (publish + one filtered subscription) ----------

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(ArceusEventsTopology.EVENTS_EXCHANGE);
    }

    @Bean
    public Queue healthEventsQueue() {
        return QueueBuilder.durable(DeploymentMessagingTopology.HEALTH_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", DeploymentMessagingTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DeploymentMessagingTopology.HEALTH_EVENTS_QUEUE)
                .build();
    }

    @Bean
    public Queue healthEventsDeadLetterQueue() {
        return new Queue(DeploymentMessagingTopology.HEALTH_EVENTS_QUEUE + DeploymentMessagingTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding healthEventsDeadLetterBinding(Queue healthEventsDeadLetterQueue, DirectExchange deploymentDeadLetterExchange) {
        return BindingBuilder.bind(healthEventsDeadLetterQueue).to(deploymentDeadLetterExchange)
                .with(DeploymentMessagingTopology.HEALTH_EVENTS_QUEUE);
    }

    /** One binding per monitoring health-transition routing key this service actually cares about - see MonitoringHealthEventsConsumer. */
    @Bean
    public Binding healthEventsServiceDownBinding(Queue healthEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(healthEventsQueue).to(eventsExchange).with(ArceusEventsTopology.RK_SERVICE_DOWN);
    }

    @Bean
    public Binding healthEventsServiceRecoveredBinding(Queue healthEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(healthEventsQueue).to(eventsExchange).with(ArceusEventsTopology.RK_SERVICE_RECOVERED);
    }

    @Bean
    public Binding healthEventsAlertTriggeredBinding(Queue healthEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(healthEventsQueue).to(eventsExchange).with(ArceusEventsTopology.RK_ALERT_TRIGGERED);
    }

    @Bean
    public Binding healthEventsAlertResolvedBinding(Queue healthEventsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(healthEventsQueue).to(eventsExchange).with(ArceusEventsTopology.RK_ALERT_RESOLVED);
    }

    // ---------- deployment-service's own internal exchange + DLX ----------

    @Bean
    public DirectExchange deploymentInternalExchange() {
        return new DirectExchange(DeploymentMessagingTopology.INTERNAL_EXCHANGE);
    }

    @Bean
    public DirectExchange deploymentDeadLetterExchange() {
        return new DirectExchange(DeploymentMessagingTopology.DEAD_LETTER_EXCHANGE);
    }

    // ---------- Approval-decisions queue ----------

    @Bean
    public Queue approvalDecisionsQueue() {
        return QueueBuilder.durable(DeploymentMessagingTopology.APPROVAL_DECISIONS_QUEUE)
                .withArgument("x-dead-letter-exchange", DeploymentMessagingTopology.DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DeploymentMessagingTopology.APPROVAL_DECISIONS_QUEUE)
                .build();
    }

    @Bean
    public Queue approvalDecisionsDeadLetterQueue() {
        return new Queue(DeploymentMessagingTopology.APPROVAL_DECISIONS_QUEUE + DeploymentMessagingTopology.DLQ_SUFFIX, true);
    }

    @Bean
    public Binding approvalDecisionsDeadLetterBinding(Queue approvalDecisionsDeadLetterQueue, DirectExchange deploymentDeadLetterExchange) {
        return BindingBuilder.bind(approvalDecisionsDeadLetterQueue).to(deploymentDeadLetterExchange)
                .with(DeploymentMessagingTopology.APPROVAL_DECISIONS_QUEUE);
    }

    @Bean
    public Binding approvalDecisionsBinding(Queue approvalDecisionsQueue, DirectExchange deploymentInternalExchange) {
        return BindingBuilder.bind(approvalDecisionsQueue).to(deploymentInternalExchange)
                .with(DeploymentMessagingTopology.APPROVAL_DECISIONS_QUEUE);
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
