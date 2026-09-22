package com.smartsolar.mobile;

import com.smartsolar.mobile.data.remote.interceptor.AuthInterceptor;
import com.smartsolar.mobile.util.SessionStore;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuthInterceptorTest {
    @Test public void sendsBearerTokenAndClearsMatchingSessionOn401() throws Exception {
        FakeSessions sessions = new FakeSessions();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(401));
            OkHttpClient client = client(sessions);
            try (Response response = client.newCall(new Request.Builder().url(server.url("/api/v1/users/me")).build()).execute()) {
                assertEquals(401, response.code());
            }
            assertEquals("Bearer test-token", server.takeRequest(2, TimeUnit.SECONDS).getHeader("Authorization"));
            assertNull(sessions.token);
        }
    }
    @Test public void anonymousLoginDoesNotSendOrClearExistingToken() throws Exception {
        FakeSessions sessions = new FakeSessions();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(401));
            try (Response response = client(sessions).newCall(new Request.Builder().url(server.url("/api/v1/auth/login")).build()).execute()) {
                assertEquals(401, response.code());
            }
            assertNull(server.takeRequest(2, TimeUnit.SECONDS).getHeader("Authorization"));
            assertEquals("test-token", sessions.token);
        }
    }
    @Test public void missingTokenIsNotSentAndServerErrorsKeepSession() throws Exception {
        FakeSessions sessions = new FakeSessions();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503));
            try (Response response = client(sessions).newCall(new Request.Builder().url(server.url("/api/v1/users/me")).build()).execute()) {
                assertEquals(503, response.code());
            }
            assertEquals("test-token", sessions.token);
            server.takeRequest(2, TimeUnit.SECONDS);
            sessions.token = null;
            server.enqueue(new MockResponse().setResponseCode(401));
            try (Response response = client(sessions).newCall(new Request.Builder().url(server.url("/api/v1/users/me")).build()).execute()) {
                assertEquals(401, response.code());
            }
            assertNull(server.takeRequest(2, TimeUnit.SECONDS).getHeader("Authorization"));
        }
    }
    private static OkHttpClient client(SessionStore sessions) {
        return new OkHttpClient.Builder().addInterceptor(new AuthInterceptor(sessions)).build();
    }
    private static final class FakeSessions implements SessionStore {
        String token = "test-token";
        public String getAccessToken() { return token; }
        public void clearIfMatches(String rejected) { if (rejected.equals(token)) token = null; }
    }
}
