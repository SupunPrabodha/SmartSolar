package com.smartsolar.mobile.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ReservationUiUtils {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private ReservationUiUtils() { }

    public static String formatUtc(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.trim().isEmpty()) {
            return "Schedule unavailable";
        }
        try {
            Instant instant = Instant.parse(isoTimestamp);
            return FORMATTER.format(instant);
        } catch (Exception exception) {
            return "Schedule unavailable";
        }
    }

    public static String formatCutoffUtc(String isoStartTimestamp) {
        if (isoStartTimestamp == null || isoStartTimestamp.trim().isEmpty()) {
            return "Cutoff unavailable";
        }
        try {
            Instant instant = Instant.parse(isoStartTimestamp).minusSeconds(12 * 3600);
            return FORMATTER.format(instant);
        } catch (Exception exception) {
            return "Cutoff unavailable";
        }
    }

    public static boolean isCutoffPassed(String isoStartTimestamp, long nowMillis) {
        if (isoStartTimestamp == null || isoStartTimestamp.trim().isEmpty()) return true;
        try {
            Instant start = Instant.parse(isoStartTimestamp);
            return (start.toEpochMilli() - nowMillis) < (12 * 3600 * 1000L);
        } catch (Exception exception) {
            return true;
        }
    }

    public static boolean isTerminalStatus(String status) {
        return "Cancelled".equalsIgnoreCase(status)
                || "Rejected".equalsIgnoreCase(status)
                || "Completed".equalsIgnoreCase(status);
    }

    public static boolean isValidGuid(String id) {
        if (id == null) return false;
        String trimmed = id.trim();
        if (trimmed.isEmpty()) return false;
        if (trimmed.replace("-", "").replaceAll("0", "").isEmpty()) return false;
        return trimmed.matches("(?i)^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$")
                || trimmed.matches("(?i)^[0-9a-f]{32}$");
    }

    public static boolean isValidEnergy(String energyStr) {
        if (energyStr == null || energyStr.trim().isEmpty()) return false;
        try {
            double val = Double.parseDouble(energyStr.trim());
            return Double.isFinite(val) && val > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
