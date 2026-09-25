package com.smartsolar.mobile;

import com.smartsolar.mobile.util.ReservationUiUtils;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReservationUiUtilsTest {
    @Test
    public void formatUtcFormatsIsoTimestampsCorrectly() {
        assertEquals("30 Sep 2026, 10:00 UTC", ReservationUiUtils.formatUtc("2026-09-30T10:00:00Z"));
        assertEquals("01 Jan 2030, 00:00 UTC", ReservationUiUtils.formatUtc("2030-01-01T00:00:00Z"));
        assertEquals("Schedule unavailable", ReservationUiUtils.formatUtc("invalid"));
        assertEquals("Schedule unavailable", ReservationUiUtils.formatUtc(null));
        assertEquals("Schedule unavailable", ReservationUiUtils.formatUtc(""));
    }

    @Test
    public void formatCutoffUtcSubtractsTwelveHours() {
        assertEquals("29 Sep 2026, 22:00 UTC", ReservationUiUtils.formatCutoffUtc("2026-09-30T10:00:00Z"));
        assertEquals("31 Dec 2029, 12:00 UTC", ReservationUiUtils.formatCutoffUtc("2030-01-01T00:00:00Z"));
        assertEquals("Cutoff unavailable", ReservationUiUtils.formatCutoffUtc("invalid"));
        assertEquals("Cutoff unavailable", ReservationUiUtils.formatCutoffUtc(null));
    }

    @Test
    public void isCutoffPassedEvaluatesTwelveHourWindow() {
        long startMillis = 1790000000000L;
        long twelveHoursBefore = startMillis - (12 * 3600 * 1000L);

        // More than 12 hours remaining -> cutoff not passed (false)
        assertFalse(ReservationUiUtils.isCutoffPassed("2026-09-30T10:00:00Z", 1000L));

        // Less than 12 hours remaining -> cutoff passed (true)
        assertTrue(ReservationUiUtils.isCutoffPassed("2026-09-30T10:00:00Z", System.currentTimeMillis() + 100000000000L));

        // Invalid timestamp -> cutoff considered passed (true) for safety
        assertTrue(ReservationUiUtils.isCutoffPassed(null, 1000L));
        assertTrue(ReservationUiUtils.isCutoffPassed("invalid", 1000L));
    }

    @Test
    public void isTerminalStatusIdentifiesNonModifiableStates() {
        assertTrue(ReservationUiUtils.isTerminalStatus("Cancelled"));
        assertTrue(ReservationUiUtils.isTerminalStatus("cancelled"));
        assertTrue(ReservationUiUtils.isTerminalStatus("Rejected"));
        assertTrue(ReservationUiUtils.isTerminalStatus("Completed"));

        assertFalse(ReservationUiUtils.isTerminalStatus("Pending"));
        assertFalse(ReservationUiUtils.isTerminalStatus("Approved"));
        assertFalse(ReservationUiUtils.isTerminalStatus(""));
        assertFalse(ReservationUiUtils.isTerminalStatus(null));
    }

    @Test
    public void isValidGuidValidatesFormatAndRejectsNilGuid() {
        assertTrue(ReservationUiUtils.isValidGuid("11111111-1111-1111-1111-111111111111"));
        assertTrue(ReservationUiUtils.isValidGuid("22222222222222222222222222222222"));
        assertTrue(ReservationUiUtils.isValidGuid("abcdef12-3456-7890-abcd-ef1234567890"));

        assertFalse(ReservationUiUtils.isValidGuid("00000000-0000-0000-0000-000000000000"));
        assertFalse(ReservationUiUtils.isValidGuid("00000000000000000000000000000000"));
        assertFalse(ReservationUiUtils.isValidGuid("not-a-guid"));
        assertFalse(ReservationUiUtils.isValidGuid(""));
        assertFalse(ReservationUiUtils.isValidGuid(null));
    }

    @Test
    public void isValidEnergyValidatesPositiveNumbers() {
        assertTrue(ReservationUiUtils.isValidEnergy("1.5"));
        assertTrue(ReservationUiUtils.isValidEnergy("100"));
        assertTrue(ReservationUiUtils.isValidEnergy("0.1"));

        assertFalse(ReservationUiUtils.isValidEnergy("0"));
        assertFalse(ReservationUiUtils.isValidEnergy("-1"));
        assertFalse(ReservationUiUtils.isValidEnergy("-5.5"));
        assertFalse(ReservationUiUtils.isValidEnergy("abc"));
        assertFalse(ReservationUiUtils.isValidEnergy(""));
        assertFalse(ReservationUiUtils.isValidEnergy(null));
    }
}
