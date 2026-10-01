package com.smartsolar.mobile;

import com.smartsolar.mobile.ui.auth.LoginErrorPresentation;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoginErrorPresentationTest {
    @Test public void rejectedCredentialsAndMobileAccessUseSameGenericCopy() {
        assertEquals(R.string.auth_signin_failed,LoginErrorPresentation.message(R.string.login_failed));
        assertEquals(R.string.auth_signin_failed,LoginErrorPresentation.message(R.string.mobile_role_not_supported));
    }
    @Test public void connectionAndServiceErrorsStayDistinctFromCredentials() {
        assertEquals(R.string.auth_offline,LoginErrorPresentation.message(R.string.connection_failed));
        assertEquals(R.string.auth_unavailable,LoginErrorPresentation.message(R.string.session_failed));
        assertNotEquals(LoginErrorPresentation.message(R.string.login_failed),LoginErrorPresentation.message(R.string.connection_failed));
    }
    @Test public void unknownErrorsAreSafeAndRateLimitDoesNotImplyWrongCredentials() {
        assertEquals(R.string.auth_unavailable,LoginErrorPresentation.message(-1));
        assertEquals(R.string.auth_rate_limited,LoginErrorPresentation.message(R.string.auth_rate_limited));
    }
}
