package com.smartsolar.mobile.ui.auth;

import com.smartsolar.mobile.R;

/** Allowlisted UI copy only; never displays response details or diagnostic identifiers. */
public final class LoginErrorPresentation {
    private LoginErrorPresentation() { }
    public static int message(int error) {
        if (error == R.string.connection_failed) return R.string.auth_offline;
        if (error == R.string.login_failed || error == R.string.mobile_role_not_supported) return R.string.auth_signin_failed;
        if (error == R.string.auth_rate_limited) return R.string.auth_rate_limited;
        return R.string.auth_unavailable;
    }
}
