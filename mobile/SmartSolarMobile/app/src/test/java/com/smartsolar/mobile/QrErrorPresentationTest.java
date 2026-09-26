package com.smartsolar.mobile;
import com.smartsolar.mobile.util.QrErrorPresentation;
import org.junit.Test;
import static org.junit.Assert.*;
public class QrErrorPresentationTest {
    @Test public void knownConflictsHaveReadableDistinctMessages() {
        assertEquals(R.string.qr_already_completed, QrErrorPresentation.resource(409, "This reservation has already been completed."));
        assertEquals(R.string.qr_cancelled, QrErrorPresentation.resource(409, "Current status: 'Cancelled'."));
        assertEquals(R.string.qr_rejected, QrErrorPresentation.resource(409, "Current status: 'Rejected'."));
        assertEquals(R.string.qr_outside_window, QrErrorPresentation.resource(409, "Transfer verification and completion require the accepted scheduled window."));
        assertEquals(R.string.qr_inactive_prosumer, QrErrorPresentation.resource(409, "The reservation requires an active Prosumer."));
        assertEquals(R.string.qr_invalid_station, QrErrorPresentation.resource(409, "The reservation station/slot linkage is missing, inactive or invalid."));
    }
    @Test public void unrecognizedErrorsNeverExposeRawDetailAndStatusWins() {
        assertEquals(R.string.qr_changed, QrErrorPresentation.resource(409, "Internal arbitrary detail"));
        assertEquals(R.string.load_failed, QrErrorPresentation.resource(500, "Database error"));
        assertEquals(R.string.session_expired, QrErrorPresentation.resource(401, "Current status: 'Cancelled'."));
        assertEquals(R.string.access_denied, QrErrorPresentation.resource(403, "Current status: 'Cancelled'."));
    }
}
