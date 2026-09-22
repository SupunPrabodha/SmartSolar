package com.smartsolar.mobile.util;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MobileAccessTest {
    @Test public void activeMobileRolesCanEnter() {
        assertTrue(MobileAccess.canEnter("Prosumer", "Active"));
        assertTrue(MobileAccess.canEnter("GridOperator", "Active"));
    }

    @Test public void backofficeAndUnknownRolesCannotEnter() {
        assertFalse(MobileAccess.canEnter("Backoffice", "Active"));
        assertFalse(MobileAccess.canEnter("Administrator", "Active"));
        assertFalse(MobileAccess.canEnter(null, "Active"));
    }

    @Test public void inactiveOrMissingStatusCannotEnter() {
        for (String role : new String[] { "Prosumer", "GridOperator" }) {
            assertFalse(MobileAccess.canEnter(role, "PendingActivation"));
            assertFalse(MobileAccess.canEnter(role, "Deactivated"));
            assertFalse(MobileAccess.canEnter(role, null));
        }
    }
}
