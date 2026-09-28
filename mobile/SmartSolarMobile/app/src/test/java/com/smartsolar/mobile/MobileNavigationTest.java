package com.smartsolar.mobile;

import com.smartsolar.mobile.util.MobileNavigation;
import com.smartsolar.mobile.util.MobileNavigation.Destination;
import com.smartsolar.mobile.util.MobileNavigation.Section;
import org.junit.Test;
import static org.junit.Assert.*;

public class MobileNavigationTest {
    @Test public void prosumerHasExactlyFiveDestinations() {
        assertEquals(java.util.Arrays.asList(Destination.HOME, Destination.STATIONS, Destination.RESERVATIONS, Destination.HISTORY, Destination.ACCOUNT), MobileNavigation.destinations("Prosumer"));
    }
    @Test public void operatorHasExactlyFiveDestinations() {
        assertEquals(java.util.Arrays.asList(Destination.HOME, Destination.STATIONS, Destination.SCAN, Destination.BOOKINGS, Destination.SEARCH), MobileNavigation.destinations("GridOperator"));
    }
    @Test public void subsectionsBelongToCorrectParent() {
        for (Section section : new Section[]{Section.MINE, Section.CURRENT, Section.PENDING, Section.SEARCH}) assertEquals(Destination.RESERVATIONS, MobileNavigation.parent("Prosumer", section));
        for (Section section : new Section[]{Section.CURRENT, Section.PENDING, Section.HISTORY}) assertEquals(Destination.BOOKINGS, MobileNavigation.parent("GridOperator", section));
        assertEquals(Destination.HISTORY, MobileNavigation.parent("Prosumer", Section.HISTORY));
        assertEquals(Destination.SEARCH, MobileNavigation.parent("GridOperator", Section.SEARCH));
    }
    @Test public void selectionMapsSecondaryScreens() {
        assertEquals(Destination.RESERVATIONS, MobileNavigation.selection("Prosumer", Destination.BOOKINGS));
        assertEquals(Destination.RESERVATIONS, MobileNavigation.selection("Prosumer", Destination.SEARCH));
        assertEquals(Destination.BOOKINGS, MobileNavigation.selection("GridOperator", Destination.HISTORY));
    }
    @Test public void reselectAndScannerDoNotReplaceContent() {
        for (Destination destination : MobileNavigation.destinations("Prosumer")) assertFalse(MobileNavigation.shouldSwitch("Prosumer", destination, destination));
        assertFalse(MobileNavigation.shouldSwitch("GridOperator", Destination.HOME, Destination.SCAN));
        assertTrue(MobileNavigation.shouldSwitch("Prosumer", Destination.HOME, Destination.STATIONS));
    }
    @Test public void backReturnsHomeWithoutTabHistory() {
        assertNull(MobileNavigation.back(Destination.HOME));
        for (Destination destination : Destination.values()) if (destination != Destination.HOME) assertEquals(Destination.HOME, MobileNavigation.back(destination));
    }
    @Test public void rolesAreIsolated() {
        assertFalse(MobileNavigation.shouldSwitch("Prosumer", Destination.HOME, Destination.SCAN));
        assertFalse(MobileNavigation.shouldSwitch("GridOperator", Destination.HOME, Destination.ACCOUNT));
        assertFalse(MobileNavigation.shouldSwitch("GridOperator", Destination.HOME, Destination.RESERVATIONS));
        assertNull(MobileNavigation.parent("GridOperator", Section.MINE));
        assertNull(MobileNavigation.selection("Prosumer", Destination.SCAN));
        assertNull(MobileNavigation.selection("GridOperator", Destination.ACCOUNT));
        assertTrue(MobileNavigation.destinations("Backoffice").isEmpty());
        assertTrue(MobileNavigation.destinations(null).isEmpty());
        assertNull(MobileNavigation.parent("Backoffice", Section.CURRENT));
    }
}
