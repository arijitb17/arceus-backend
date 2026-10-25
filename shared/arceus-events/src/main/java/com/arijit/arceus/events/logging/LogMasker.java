package com.arijit.arceus.events.logging;

public final class LogMasker {

    private static final String MASK = "***";

    private LogMasker() {
    }

    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "<none>";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return MASK;
        }
        return email.charAt(0) + MASK + email.substring(at);
    }

    public static String recipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "<none>";
        }
        return recipient.contains("@") ? email(recipient) : phone(recipient);
    }

    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "<none>";
        }
        if (phone.length() <= 4) {
            return MASK;
        }
        return MASK + phone.substring(phone.length() - 4);
    }
}
