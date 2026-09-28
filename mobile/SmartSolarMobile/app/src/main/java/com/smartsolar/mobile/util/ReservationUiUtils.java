package com.smartsolar.mobile.util;

import java.time.Instant;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ReservationUiUtils {
    private static DateTimeFormatter formatter() {
        return DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault());
    }

    private ReservationUiUtils() { }

    public static String formatUtc(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.trim().isEmpty()) {
            return "Schedule unavailable";
        }
        try {
            Instant instant = Instant.parse(isoTimestamp);
            return formatter().format(instant);
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
            return formatter().format(instant);
        } catch (Exception exception) {
            return "Cutoff unavailable";
        }
    }

    public static String formatTime(String value) {
        try { return DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(Instant.parse(value)); }
        catch (RuntimeException error) { return "Time unavailable"; }
    }
    public static String shortReference(String value) {
        if (value == null || value.isEmpty()) return "Unavailable";
        return value.length() > 14 ? value.substring(0, 8).toUpperCase(Locale.ROOT) + "…" + value.substring(value.length() - 4).toUpperCase(Locale.ROOT) : value;
    }
    public static String schedule(String start, String end) {
        try {
            java.time.ZonedDateTime a = Instant.parse(start).atZone(ZoneId.systemDefault()), b = Instant.parse(end).atZone(ZoneId.systemDefault());
            DateTimeFormatter day = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault());
            return a.format(day) + (a.toLocalDate().equals(b.toLocalDate()) ? "" : " – " + b.format(day)) + "\n" + formatTime(start) + " – " + formatTime(end);
        } catch (RuntimeException error) { return "Schedule unavailable"; }
    }
    public static String greeting() {
        int hour = java.time.LocalTime.now().getHour();
        return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
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

        String key = status.toLowerCase(Locale.ROOT);
        int textRes, backgroundRes;
        switch (key) {
            case "active":
            case "approved": textRes = com.smartsolar.mobile.R.color.solar_status_approved; backgroundRes = com.smartsolar.mobile.R.color.solar_approved_surface; break;
            case "pending": textRes = com.smartsolar.mobile.R.color.solar_status_pending; backgroundRes = com.smartsolar.mobile.R.color.solar_pending_surface; break;
            case "rejected": textRes = com.smartsolar.mobile.R.color.solar_status_rejected; backgroundRes = com.smartsolar.mobile.R.color.solar_rejected_surface; break;
            case "completed": textRes = com.smartsolar.mobile.R.color.solar_status_completed; backgroundRes = com.smartsolar.mobile.R.color.solar_completed_surface; break;
            default: textRes = com.smartsolar.mobile.R.color.solar_status_cancelled; backgroundRes = com.smartsolar.mobile.R.color.solar_cancelled_surface;
        }
        int textColor = view.getContext().getColor(textRes), bgColor = view.getContext().getColor(backgroundRes);
        view.setTextColor(textColor);

        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        shape.setCornerRadius(view.getResources().getDisplayMetrics().density * 6);
        shape.setColor(bgColor);
        view.setBackground(shape);
    }
}
