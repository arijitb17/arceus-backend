package com.arijit.arceus.events.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OtpRedisKeysTest {

    @ParameterizedTest
    @EnumSource(OtpPurpose.class)
    void emailOtpKeyContainsPurposeAndEmail(OtpPurpose purpose) {
        assertThat(OtpRedisKeys.emailOtp("a@b.com", purpose))
                .isEqualTo("otp:email:" + purpose.name() + ":a@b.com");
    }

    @Test
    void emailOtpKeysDifferPerPurpose() {
        assertThat(OtpRedisKeys.emailOtp("a@b.com", OtpPurpose.LOGIN_2FA))
                .isNotEqualTo(OtpRedisKeys.emailOtp("a@b.com", OtpPurpose.FORGOT_PASSWORD));
    }

    @Test
    void phoneSessionKey() {
        assertThat(OtpRedisKeys.phoneSession("+911234567890")).isEqualTo("otp:phone:session:+911234567890");
    }

    @Test
    void resetTokenKey() {
        assertThat(OtpRedisKeys.resetToken("tok123")).isEqualTo("reset-token:tok123");
    }

    @Test
    void pendingChangeKeysAreScopedByUser() {
        UUID id = UUID.randomUUID();
        assertThat(OtpRedisKeys.pendingEmailChange(id)).isEqualTo("pending-email-change:" + id);
        assertThat(OtpRedisKeys.pendingPhoneChange(id)).isEqualTo("pending-phone-change:" + id);
        assertThat(OtpRedisKeys.pendingEmailChange(id)).isNotEqualTo(OtpRedisKeys.pendingPhoneChange(id));
    }
}
