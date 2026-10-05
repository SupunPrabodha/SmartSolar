package com.smartsolar.mobile.util;

import java.util.Locale;

/** Presentation only; raw immutable IDs must still be used for requests and QR/navigation. */
public final class DisplayReference {
    private DisplayReference() { }
    public static String reservation(String id) { return format(id, "REF"); }
    public static String station(String id) { return format(id, "STN"); }
    public static String slot(String id) { return format(id, "SLOT"); }

    /** Returns a real ID only for one unambiguous reference in the loaded authorized records. */
    public static String resolveSlot(String reference, Iterable<String> ids) {
        if (reference == null || reference.trim().isEmpty()) return "";
        String found = "";
        for (String id : ids) {
            if (slot(id).equalsIgnoreCase(reference.trim()) && !"Unavailable".equals(slot(id))) {
                if (!found.isEmpty() && !found.replace("-", "").equalsIgnoreCase(id.replace("-", ""))) return "";
                found = id;
            }
        }
        return found;
    }

    private static String format(String id, String prefix) {
        if (id == null) return "Unavailable";
        String value = id.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("(?:[a-f0-9]{32}|[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})")) return "Unavailable";
        String canonical = value.replace("-", "");
        if (canonical.matches("0+")) return "Unavailable";
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < canonical.length(); index++) {
            hash = (hash ^ canonical.charAt(index)) * 1099511628211L;
        }
        String suffix = Long.toString(Long.remainderUnsigned(hash, 3656158440062976L), 36).toUpperCase(Locale.ROOT);
        return prefix + "-" + "0000000000".substring(suffix.length()) + suffix;
    }
}
