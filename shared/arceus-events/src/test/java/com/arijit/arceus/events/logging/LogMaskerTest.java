package com.arijit.arceus.events.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskerTest {

    @Nested
    @DisplayName("email()")
    class Email {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "   "})
        void returnsPlaceholderForNullOrBlank(String input) {
            assertThat(LogMasker.email(input)).isEqualTo("<none>");
        }

        @Test
        void keepsFirstCharAndDomain() {
            assertThat(LogMasker.email("john.doe@example.com")).isEqualTo("j***@example.com");
        }

        @Test
        void masksSingleCharLocalPart() {
            assertThat(LogMasker.email("a@b.io")).isEqualTo("a***@b.io");
        }

        @ParameterizedTest
        @ValueSource(strings = {"no-at-sign", "@leading-at"})
        void masksEntirelyWhenAtSignMissingOrLeading(String input) {
            assertThat(LogMasker.email(input)).isEqualTo("***");
        }

        @Test
        void neverLeaksLocalPart() {
            assertThat(LogMasker.email("secretuser@corp.com")).doesNotContain("ecretuser");
        }
    }

    @Nested
    @DisplayName("phone()")
    class Phone {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        void returnsPlaceholderForNullOrBlank(String input) {
            assertThat(LogMasker.phone(input)).isEqualTo("<none>");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1", "1234", "12"})
        void masksFullyWhenFourCharsOrFewer(String input) {
            assertThat(LogMasker.phone(input)).isEqualTo("***");
        }

        @Test
        void keepsLastFourDigits() {
            assertThat(LogMasker.phone("+919876543210")).isEqualTo("***3210");
        }

        @Test
        void fiveCharsKeepsLastFour() {
            assertThat(LogMasker.phone("12345")).isEqualTo("***2345");
        }
    }

    @Nested
    @DisplayName("recipient()")
    class Recipient {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        void returnsPlaceholderForNullOrBlank(String input) {
            assertThat(LogMasker.recipient(input)).isEqualTo("<none>");
        }

        @Test
        void delegatesToEmailWhenContainsAt() {
            assertThat(LogMasker.recipient("jane@site.org")).isEqualTo("j***@site.org");
        }

        @Test
        void delegatesToPhoneOtherwise() {
            assertThat(LogMasker.recipient("+14155550123")).isEqualTo("***0123");
        }
    }
}
