package com.arijit.arceus.events.topology;

import com.arijit.arceus.events.deployment.DeploymentMessagingTopology;
import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.otp.OtpMessagingTopology;
import com.arijit.arceus.events.scheduler.SchedulerMessagingTopology;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the wire contract between publishers and consumers: a silent change to any of these
 * strings breaks messaging across services.
 */
class TopologyConstantsTest {

    @Test
    void routingKeysAreUniqueAndLowercaseDotted() throws Exception {
        List<String> keys = new ArrayList<>();
        for (Field f : ArceusEventsTopology.class.getDeclaredFields()) {
            if (f.getName().startsWith("RK_")) {
                keys.add((String) f.get(null));
            }
        }
        assertThat(keys).isNotEmpty();
        assertThat(new HashSet<>(keys)).hasSameSizeAs(keys);
        assertThat(keys).allMatch(k -> k.matches("[a-z_]+\\.[a-z_]+"));
    }

    @Test
    void wildcardRoutingKeyMatchesEverything() {
        assertThat(ArceusEventsTopology.ROUTING_KEY_ALL).isEqualTo("#");
    }

    @Test
    void knownTopologyNames() {
        assertThat(ArceusEventsTopology.EVENTS_EXCHANGE).isEqualTo("arceus.events.exchange");
        assertThat(ArceusEventsTopology.NOTIFICATION_QUEUE).isEqualTo("arceus.notification.events.queue");
        assertThat(ArceusEventsTopology.AUDIT_QUEUE).isEqualTo("arceus.audit.events.queue");
        assertThat(OtpMessagingTopology.OTP_QUEUE).isEqualTo("otp.notifications.queue");
        assertThat(OtpMessagingTopology.OTP_EXCHANGE).isEqualTo("otp.notifications.exchange");
        assertThat(OtpMessagingTopology.OTP_ROUTING_KEY).isEqualTo("otp.notifications.send");
    }

    @Test
    void dlqSuffixesAreConsistent() {
        assertThat(ArceusEventsTopology.DLQ_SUFFIX).isEqualTo(".dlq");
        assertThat(SchedulerMessagingTopology.DLQ_SUFFIX).isEqualTo(".dlq");
        assertThat(DeploymentMessagingTopology.DLQ_SUFFIX).isEqualTo(".dlq");
    }

    @Test
    void schedulerAndDeploymentNamesAreNamespaced() {
        assertThat(SchedulerMessagingTopology.RETRY_REQUEST_QUEUE).startsWith("arceus.scheduler.");
        assertThat(SchedulerMessagingTopology.DELAY_QUEUE).startsWith("arceus.scheduler.");
        assertThat(SchedulerMessagingTopology.DELAY_READY_QUEUE).startsWith("arceus.scheduler.");
        assertThat(SchedulerMessagingTopology.INTERNAL_EXCHANGE).startsWith("arceus.scheduler.");
        assertThat(DeploymentMessagingTopology.APPROVAL_DECISIONS_QUEUE).startsWith("arceus.deployment.");
        assertThat(DeploymentMessagingTopology.HEALTH_EVENTS_QUEUE).startsWith("arceus.deployment.");
        assertThat(DeploymentMessagingTopology.MODULE_PROVISIONING_QUEUE).startsWith("arceus.deployment.");
        assertThat(DeploymentMessagingTopology.INTERNAL_EXCHANGE).startsWith("arceus.deployment.");
    }

    @Test
    void queueNamesDoNotCollideAcrossDomains() {
        Set<String> all = new HashSet<>(List.of(
                ArceusEventsTopology.NOTIFICATION_QUEUE, ArceusEventsTopology.AUDIT_QUEUE,
                OtpMessagingTopology.OTP_QUEUE,
                SchedulerMessagingTopology.RETRY_REQUEST_QUEUE, SchedulerMessagingTopology.DELAY_QUEUE,
                SchedulerMessagingTopology.DELAY_READY_QUEUE,
                DeploymentMessagingTopology.APPROVAL_DECISIONS_QUEUE, DeploymentMessagingTopology.HEALTH_EVENTS_QUEUE,
                DeploymentMessagingTopology.MODULE_PROVISIONING_QUEUE));
        assertThat(all).hasSize(9);
    }

    @ParameterizedTest
    @ValueSource(classes = {ArceusEventsTopology.class, OtpMessagingTopology.class,
            SchedulerMessagingTopology.class, DeploymentMessagingTopology.class})
    void utilityClassesAreFinalWithPrivateConstructor(Class<?> type) throws Exception {
        assertThat(Modifier.isFinal(type.getModifiers())).isTrue();
        Constructor<?> ctor = type.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
    }
}
