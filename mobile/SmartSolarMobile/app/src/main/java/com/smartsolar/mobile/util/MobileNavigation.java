package com.smartsolar.mobile.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Presentation destinations only; authorization remains with the existing session and API. */
public final class MobileNavigation {
    public enum Destination { HOME, STATIONS, RESERVATIONS, HISTORY, ACCOUNT, SCAN, BOOKINGS, SEARCH }
    private MobileNavigation() { }
    public static List<Destination> destinations(String role) {
        if ("Prosumer".equals(role)) return Arrays.asList(Destination.HOME, Destination.STATIONS, Destination.RESERVATIONS, Destination.HISTORY, Destination.ACCOUNT);
        if ("GridOperator".equals(role)) return Arrays.asList(Destination.HOME, Destination.STATIONS, Destination.SCAN, Destination.BOOKINGS, Destination.SEARCH);
        return Collections.emptyList();
    }
    public static Destination selection(String role, Destination screen) {
        if (destinations(role).contains(screen)) return screen;
        if ("Prosumer".equals(role) && (screen == Destination.BOOKINGS || screen == Destination.SEARCH)) return Destination.RESERVATIONS;
        if ("GridOperator".equals(role) && screen == Destination.HISTORY) return Destination.BOOKINGS;
        return null;
    }
}
