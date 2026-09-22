package com.smartsolar.mobile.util;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** Local expiry is a client convenience; the API still validates every JWT. */
public final class SessionExpiry {
    private SessionExpiry() { }

    public static long parse(String value) {
        try {
            return value == null ? 0 : Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException | ArithmeticException exception) {
            return 0;
        }
    }

    public static boolean isValid(String token, long expiry, long now) {
        return token != null && !token.trim().isEmpty() && expiry > now;
    }
}
