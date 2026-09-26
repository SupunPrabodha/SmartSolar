package com.smartsolar.mobile;

import com.smartsolar.mobile.util.MobileNavigation;
import com.smartsolar.mobile.util.MobileNavigation.Destination;
import org.junit.Test;
import static org.junit.Assert.*;

public class MobileNavigationTest {
    @Test public void prosumerHasFiveDestinationsAndNoScanner() {
        assertEquals(java.util.Arrays.asList(Destination.HOME, Destination.STATIONS, Destination.RESERVATIONS, Destination.HISTORY, Destination.ACCOUNT), MobileNavigation.destinations("Prosumer"));
        assertEquals(Destination.RESERVATIONS, MobileNavigation.selection("Prosumer", Destination.BOOKINGS));
        assertEquals(Destination.RESERVATIONS, MobileNavigation.selection("Prosumer", Destination.SEARCH));
        assertNull(MobileNavigation.selection("Prosumer", Destination.SCAN));
    }
    @Test public void operatorHasCentralScanAndNoProsumerAccountOrIssuance() {
        assertEquals(java.util.Arrays.asList(Destination.HOME, Destination.STATIONS, Destination.SCAN, Destination.BOOKINGS, Destination.SEARCH), MobileNavigation.destinations("GridOperator"));
        assertEquals(Destination.BOOKINGS, MobileNavigation.selection("GridOperator", Destination.HISTORY));
        assertNull(MobileNavigation.selection("GridOperator", Destination.ACCOUNT));
        assertNull(MobileNavigation.selection("GridOperator", Destination.RESERVATIONS));
    }
    @Test public void unsupportedRolesHaveNoMobileDestinations() {
        assertTrue(MobileNavigation.destinations("Backoffice").isEmpty());
        assertTrue(MobileNavigation.destinations(null).isEmpty());
        assertNull(MobileNavigation.selection("Backoffice", Destination.HOME));
    }
}
