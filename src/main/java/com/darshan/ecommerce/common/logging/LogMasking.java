package com.darshan.ecommerce.common.logging;

public final class LogMasking {

    private LogMasking() {
    }

    // "darshan@example.com" becomes "d***@example.com": enough to correlate, not enough to identify.
    public static String email(String email) {
        if (email == null) {
            return "null";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
