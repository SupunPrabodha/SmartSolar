package com.smartsolar.mobile;
import static org.junit.Assert.*;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.interceptor.AuthInterceptor;
import com.smartsolar.mobile.util.SessionStore;
import com.smartsolar.mobile.util.EnterpriseFeedback;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
public final class EnterpriseApiTest {
    private static final class Sessions implements SessionStore {
        String token="fixture-session";
        public String getAccessToken(){return token;}
        public void clearIfMatches(String value){if(value.equals(token))token=null;}
    }
    private ApiService api(MockWebServer server,Sessions session){
        return new Retrofit.Builder().baseUrl(server.url("/api/v1/"))
            .client(new OkHttpClient.Builder().addInterceptor(new AuthInterceptor(session)).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService.class);
    }
    @Test public void recoveryIsAnonymousAndKeepsExistingSession()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.start();Sessions sessions=new Sessions();
            server.enqueue(new MockResponse().setBody("{\"message\":\"Generic response\"}"));
            api(server,sessions).forgotPassword(Map.of("identifier","fixture@example.invalid")).execute();
            okhttp3.mockwebserver.RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);
            assertEquals("/api/v1/auth/forgot-password",request.getPath());assertNull(request.getHeader("Authorization"));
            assertEquals("fixture-session",sessions.token);
        }
    }
    @Test public void changePasswordUsesAuthenticatedPrincipalAnd401ClearsSession()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.start();Sessions sessions=new Sessions();server.enqueue(new MockResponse().setResponseCode(401));
            api(server,sessions).changePassword(Map.of("currentPassword","fixture-current","newPassword","fixture-new")).execute();
            okhttp3.mockwebserver.RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS);
            assertEquals("/api/v1/users/me/change-password",request.getPath());assertEquals("Bearer fixture-session",request.getHeader("Authorization"));
            assertFalse(request.getBody().readUtf8().contains("nic"));assertNull(sessions.token);
        }
    }
    @Test public void notificationReadContractsAreOwnUserScoped()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.start();Sessions sessions=new Sessions();ApiService api=api(server,sessions);
            server.enqueue(new MockResponse().setBody("{\"unreadCount\":1,\"items\":[{\"id\":\"notice\",\"priority\":\"High\",\"category\":\"Security\"}]}"));
            assertEquals("High",api.notifications().execute().body().items.get(0).priority);
            assertEquals("/api/v1/notifications",server.takeRequest().getPath());
            server.enqueue(new MockResponse().setResponseCode(204));api.readNotification("notice").execute();
            assertEquals("/api/v1/notifications/notice/read",server.takeRequest().getPath());
            server.enqueue(new MockResponse().setResponseCode(204));api.readNotifications().execute();
            assertEquals("/api/v1/notifications/read-all",server.takeRequest().getPath());
        }
    }
    @Test public void profileMetadataHasNoImageBytes()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.start();server.enqueue(new MockResponse().setBody("{\"nic\":\"fixture\",\"profileComplete\":true,\"avatarVersion\":\"revision\"}"));
            com.smartsolar.mobile.data.remote.dto.UserResponse user=api(server,new Sessions()).getCurrentUser().execute().body();
            assertTrue(user.isProfileComplete());assertEquals("revision",user.getAvatarVersion());
        }
    }
    @Test public void outageHidesInternalDetailsAndKeepsSafeReference()throws Exception{
        try(MockWebServer server=new MockWebServer()){
            server.start();server.enqueue(new MockResponse().setResponseCode(503).setHeader("X-Correlation-ID","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .setBody("{\"detail\":\"internal-private-path\"}"));
            String message=EnterpriseFeedback.problem(api(server,new Sessions()).notifications().execute());
            assertTrue(message.contains("Service unavailable"));assertTrue(message.contains("aaaaaaaa"));assertFalse(message.contains("internal-private-path"));
        }
    }
}
