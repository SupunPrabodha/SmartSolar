package com.smartsolar.mobile;

import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationDashboardSummaryResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.*;

public class ReservationApiServiceTest {
    private MockWebServer server;
    private ApiService api;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void getDashboardSummaryHitsExpectedEndpoint() throws Exception {
        String json = "{\"pendingReservations\":3,\"approvedFutureReservations\":9,\"generatedAtUtc\":\"2030-01-01T00:00:00Z\"}";
        server.enqueue(new MockResponse().setBody(json).setHeader("Content-Type", "application/json"));

        Response<ReservationDashboardSummaryResponse> response = api.getDashboardSummary().execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertEquals(3, response.body().getPendingReservations());
        assertEquals(9, response.body().getApprovedFutureReservations());

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/reservations/dashboard-summary", request.getPath());
        assertEquals("GET", request.getMethod());
    }

    @Test
    public void getCurrentBookingsPassesQueryParameters() throws Exception {
        String json = "{\"items\":[],\"page\":2,\"pageSize\":10,\"hasMore\":false}";
        server.enqueue(new MockResponse().setBody(json).setHeader("Content-Type", "application/json"));

        Map<String, String> query = new HashMap<>();
        query.put("page", "2");
        query.put("pageSize", "10");

        Response<ReservationPageResponse> response = api.getCurrentBookings(query).execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertEquals(2, response.body().getPage());

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertTrue(request.getPath().startsWith("/reservations/current"));
        assertTrue(request.getPath().contains("page=2"));
        assertTrue(request.getPath().contains("pageSize=10"));
    }

    @Test
    public void getBookingHistoryHitsExpectedEndpoint() throws Exception {
        String json = "{\"items\":[],\"page\":1,\"pageSize\":20,\"hasMore\":false}";
        server.enqueue(new MockResponse().setBody(json).setHeader("Content-Type", "application/json"));

        Response<ReservationPageResponse> response = api.getBookingHistory(new HashMap<>()).execute();

        assertTrue(response.isSuccessful());
        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertTrue(request.getPath().startsWith("/reservations/history"));
    }
}
