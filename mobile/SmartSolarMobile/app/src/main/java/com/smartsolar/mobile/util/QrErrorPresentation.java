package com.smartsolar.mobile.util;

import com.smartsolar.mobile.R;
import java.util.Locale;

/** Maps known API failure categories to fixed, user-facing copy; never echoes arbitrary server text. */
public final class QrErrorPresentation {
    private QrErrorPresentation() { }
    public static int resource(int status, String detail) {
        if (status == 401) return R.string.session_expired;
        if (status == 403) return R.string.access_denied;
        if (status != 409) return R.string.load_failed;
        String message = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (message.contains("already been completed") || message.contains("'completed'")) return R.string.qr_already_completed;
        if (message.contains("'cancelled'")) return R.string.qr_cancelled;
        if (message.contains("'rejected'")) return R.string.qr_rejected;
        if (message.contains("scheduled window")) return R.string.qr_outside_window;
        if (message.contains("active prosumer")) return R.string.qr_inactive_prosumer;
        if (message.contains("station/slot linkage")) return R.string.qr_invalid_station;
        if (message.contains("schedule requires")) return R.string.qr_invalid_schedule;
        return R.string.qr_changed;
    }
}
