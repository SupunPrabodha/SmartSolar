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

    public static void formatStatusBadge(android.widget.TextView view, String status) {
        if (view == null) return;
        if (status == null || status.trim().isEmpty()) {
            view.setVisibility(android.view.View.GONE);
            return;
        }
        view.setVisibility(android.view.View.VISIBLE);
        view.setText(status);

        int textColor;
        int bgColor;
        if ("Approved".equalsIgnoreCase(status)) {
            textColor = 0xFF2E7D32; // Green
            bgColor = 0x1F2E7D32;
        } else if ("Pending".equalsIgnoreCase(status)) {
            textColor = 0xFFE65100; // Orange
            bgColor = 0x1FE65100;
        } else if ("Rejected".equalsIgnoreCase(status)) {
            textColor = 0xFFC62828; // Red
            bgColor = 0x1FC62828;
        } else if ("Cancelled".equalsIgnoreCase(status)) {
            textColor = 0xFF757575; // Grey
            bgColor = 0x1F757575;
        } else if ("Completed".equalsIgnoreCase(status)) {
            textColor = 0xFF1565C0; // Blue
            bgColor = 0x1F1565C0;
        } else {
            textColor = 0xFF757575;
            bgColor = 0x1F000000;
        }
        view.setTextColor(textColor);

        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(view.getResources().getDisplayMetrics().density * 6);
        shape.setColor(bgColor);
        view.setBackground(shape);
    }
}
