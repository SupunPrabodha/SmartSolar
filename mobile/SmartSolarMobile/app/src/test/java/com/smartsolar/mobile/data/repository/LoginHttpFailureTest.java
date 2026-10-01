package com.smartsolar.mobile.data.repository;

import com.smartsolar.mobile.R;
import com.smartsolar.mobile.ui.auth.LoginErrorPresentation;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Tests the repository failure category through the user-facing allowlist. */
public class LoginHttpFailureTest {
    private int message(int status) {
        return LoginErrorPresentation.message(AuthRepository.loginFailureResource(status));
    }
    @Test public void rejectedCredentialsStayGeneric() {
        for (int status : new int[]{400, 401, 403}) {
            assertEquals(R.string.auth_signin_failed, message(status));
        }
    }
    @Test public void throttlingIsNotCredentialRejection() {
        assertEquals(R.string.auth_rate_limited, message(429));
    }
    @Test public void serverErrorsAreUnavailable() {
        for (int status = 500; status <= 599; status++) {
            assertEquals(R.string.auth_unavailable, message(status));
        }
    }
    @Test public void missingSuccessBodyIsUnavailable() {
        assertEquals(R.string.auth_unavailable, message(200));
        assertEquals(R.string.auth_unavailable, message(204));
    }
}
