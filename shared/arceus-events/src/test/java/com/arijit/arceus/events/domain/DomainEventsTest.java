package com.arijit.arceus.events.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventsTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID KEY = UUID.randomUUID();

    static Stream<Arguments> events() {
        return Stream.of(
                Arguments.of(UserRegisteredEvent.now(USER, "a@b.com", "Ash"), "USER_REGISTERED", ArceusEventsTopology.RK_USER_REGISTERED),
                Arguments.of(LoginSuccessfulEvent.now(USER, "a@b.com"), "LOGIN_SUCCESSFUL", ArceusEventsTopology.RK_LOGIN_SUCCESSFUL),
                Arguments.of(LoginFailedEvent.now("a@b.com", "bad password"), "LOGIN_FAILED", ArceusEventsTopology.RK_LOGIN_FAILED),
                Arguments.of(PasswordChangedEvent.now(USER, "a@b.com"), "PASSWORD_CHANGED", ArceusEventsTopology.RK_PASSWORD_CHANGED),
                Arguments.of(EmailChangedEvent.now(USER, "old@b.com", "new@b.com"), "EMAIL_CHANGED", ArceusEventsTopology.RK_EMAIL_CHANGED),
                Arguments.of(PhoneChangedEvent.now(USER, "111", "222"), "PHONE_CHANGED", ArceusEventsTopology.RK_PHONE_CHANGED),
                Arguments.of(UserLoggedOutEvent.now(USER, "a@b.com"), "USER_LOGGED_OUT", ArceusEventsTopology.RK_USER_LOGGED_OUT),
                Arguments.of(ApiKeyCreatedEvent.now(USER, "a@b.com", KEY, "ak_123", "ci"), "API_KEY_CREATED", ArceusEventsTopology.RK_API_KEY_CREATED),
                Arguments.of(ApiKeyRevokedEvent.now(USER, "a@b.com", KEY), "API_KEY_REVOKED", ArceusEventsTopology.RK_API_KEY_REVOKED)
        );
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("events")
    void exposesTypeRoutingKeyAndTimestamp(DomainEvent event, String expectedType, String expectedRoutingKey) {
        assertThat(event.eventType()).isEqualTo(expectedType);
        assertThat(event.routingKey()).isEqualTo(expectedRoutingKey);
        assertThat(event.occurredAt()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(Duration.ofSeconds(5)));
    }

    @Test
    void routingKeysAreUniqueAcrossEvents() {
        long distinct = events().map(a -> ((DomainEvent) a.get()[0]).routingKey()).distinct().count();
        assertThat(distinct).isEqualTo(events().count());
    }

    @Test
    void factoryMethodsCarryPayloadFields() {
        UserRegisteredEvent reg = UserRegisteredEvent.now(USER, "a@b.com", "Ash");
        assertThat(reg.userId()).isEqualTo(USER);
        assertThat(reg.email()).isEqualTo("a@b.com");
        assertThat(reg.name()).isEqualTo("Ash");

        EmailChangedEvent changed = EmailChangedEvent.now(USER, "old@b.com", "new@b.com");
        assertThat(changed.oldEmail()).isEqualTo("old@b.com");
        assertThat(changed.newEmail()).isEqualTo("new@b.com");

        PhoneChangedEvent phone = PhoneChangedEvent.now(USER, "111", "222");
        assertThat(phone.oldPhoneNumber()).isEqualTo("111");
        assertThat(phone.newPhoneNumber()).isEqualTo("222");

        ApiKeyCreatedEvent created = ApiKeyCreatedEvent.now(USER, "a@b.com", KEY, "ak_1", "ci");
        assertThat(created.apiKeyId()).isEqualTo(KEY);
        assertThat(created.keyPrefix()).isEqualTo("ak_1");
        assertThat(created.name()).isEqualTo("ci");

        LoginFailedEvent failed = LoginFailedEvent.now("a@b.com", "locked");
        assertThat(failed.reason()).isEqualTo("locked");
    }

    @Test
    void recordsWithEqualValuesAreEqual() {
        Instant t = Instant.now();
        assertThat(new LoginFailedEvent("a", "r", t)).isEqualTo(new LoginFailedEvent("a", "r", t));
    }

    @Test
    void correlationConstants() {
        assertThat(CorrelationConstants.HEADER_NAME).isEqualTo("X-Request-Id");
        assertThat(CorrelationConstants.MDC_KEY).isEqualTo("correlationId");
    }
}
