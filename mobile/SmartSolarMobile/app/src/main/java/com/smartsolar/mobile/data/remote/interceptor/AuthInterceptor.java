package com.smartsolar.mobile.data.remote.interceptor;

import com.smartsolar.mobile.util.SessionStore;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public final class AuthInterceptor implements Interceptor {
    private final SessionStore sessions;

    public AuthInterceptor(SessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        // Login is anonymous and must not send a previous account's token.
        String path = original.url().encodedPath();
        String token = path.endsWith("/auth/login") || path.endsWith("/auth/register-prosumer") ? null : sessions.getAccessToken();
        Request.Builder request = original.newBuilder().removeHeader("Authorization");
        if (token != null) request.header("Authorization", "Bearer " + token);
        Response response = chain.proceed(request.build());
        if (response.code() == 401 && token != null) {
            try {
                sessions.clearIfMatches(token);
            } catch (RuntimeException exception) {
                response.close();
                throw exception;
            }
        }
        return response;
    }
}
