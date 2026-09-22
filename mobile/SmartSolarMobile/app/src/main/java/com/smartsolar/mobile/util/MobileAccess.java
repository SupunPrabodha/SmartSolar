package com.smartsolar.mobile.util;

/** Client navigation policy only. The API remains responsible for authorization. */
public final class MobileAccess {
    private MobileAccess() { }

    public static boolean canEnter(String role, String status) {
        return "Active".equals(status) && ("Prosumer".equals(role) || "GridOperator".equals(role));
    }
}
