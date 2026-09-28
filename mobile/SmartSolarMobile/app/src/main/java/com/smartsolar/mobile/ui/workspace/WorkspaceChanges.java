package com.smartsolar.mobile.ui.workspace;

/** In-process invalidation signal only; carries no user, reservation or session data. */
public final class WorkspaceChanges {
    private static final java.util.concurrent.atomic.AtomicLong BOOKINGS = new java.util.concurrent.atomic.AtomicLong();
    private WorkspaceChanges() { }
    public static long bookings() { return BOOKINGS.get(); }
    public static void reservationsChanged() { BOOKINGS.incrementAndGet(); }
}
