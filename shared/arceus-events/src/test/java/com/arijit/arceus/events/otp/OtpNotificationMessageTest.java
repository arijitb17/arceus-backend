package com.arijit.arceus.events.otp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OtpNotificationMessageTest {

    @Test
    void ofPopulatesAllFieldsAndGeneratesUuidMessageId() {
        OtpNotificationMessage msg = OtpNotificationMessage.of(
                OtpChannel.EMAIL, "a@b.com", "123456", OtpPurpose.LOGIN_2FA, "Ash");

        assertThat(UUID.fromString(msg.messageId())).isNotNull();
        assertThat(msg.channel()).isEqualTo(OtpChannel.EMAIL);
        assertThat(msg.recipient()).isEqualTo("a@b.com");
        assertThat(msg.otpCode()).isEqualTo("123456");
        assertThat(msg.purpose()).isEqualTo(OtpPurpose.LOGIN_2FA);
        assertThat(msg.recipientName()).isEqualTo("Ash");
    }

    @Test
    void eachMessageGetsUniqueId() {
        OtpNotificationMessage a = OtpNotificationMessage.of(OtpChannel.SMS, "1", "1", OtpPurpose.PHONE_VERIFICATION, "n");
        OtpNotificationMessage b = OtpNotificationMessage.of(OtpChannel.SMS, "1", "1", OtpPurpose.PHONE_VERIFICATION, "n");
        assertThat(a.messageId()).isNotEqualTo(b.messageId());
    }

    @Test
    void survivesJavaSerializationRoundTrip() throws Exception {
        OtpNotificationMessage original = OtpNotificationMessage.of(
                OtpChannel.SMS, "+911234567890", "999999", OtpPurpose.EMAIL_CHANGE, "Misty");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(original);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo(original);
        }
    }
}
