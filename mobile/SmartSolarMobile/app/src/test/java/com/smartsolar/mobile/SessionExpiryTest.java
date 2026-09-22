package com.smartsolar.mobile;

import com.smartsolar.mobile.util.SessionExpiry;
import org.junit.Test;
import static org.junit.Assert.*;

public class SessionExpiryTest {
    @Test public void parsesApiUtcTimestampWithFractionalSeconds() {
        assertEquals(1790000000123L, SessionExpiry.parse("2026-09-21T14:13:20.1234567Z"));
    }
    @Test public void malformedOrMissingExpiryCannotCreateSession() {
        assertEquals(0, SessionExpiry.parse(null));
        assertEquals(0, SessionExpiry.parse("not-a-date"));
        assertEquals(0, SessionExpiry.parse("2026-09-21T14:13:20"));
    }
    @Test public void expiresAtBoundaryAndRequiresToken() {
        assertTrue(SessionExpiry.isValid("test-token", 1001, 1000));
        assertFalse(SessionExpiry.isValid("test-token", 1000, 1000));
        assertFalse(SessionExpiry.isValid("test-token", 999, 1000));
        assertFalse(SessionExpiry.isValid(null, 1001, 1000));
        assertFalse(SessionExpiry.isValid(" ", 1001, 1000));
    }
}
