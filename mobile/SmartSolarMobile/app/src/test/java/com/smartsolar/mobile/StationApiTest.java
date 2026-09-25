package com.smartsolar.mobile;
import static org.junit.Assert.*;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.NearbyStationResponse;
import com.smartsolar.mobile.data.remote.dto.SlotResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** JVM contract tests use synthetic HTTP fixtures, not claimed device or Google Maps execution. */
public final class StationApiTest {
    private ApiService api(MockWebServer server) {
        return new Retrofit.Builder().baseUrl(server.url("/api/v1/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService.class);
    }
    @Test public void nearbySendsCoordinatesAndUsesServerDistance() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(
                "[{\"station\":{\"stationId\":\"node\",\"name\":\"Fixture\",\"latitude\":6.9,\"longitude\":79.8,\"isActive\":true},\"distanceKm\":1.25}]"));
            List<NearbyStationResponse> rows = api(server).nearbyStations(6.91, 79.81, 25).execute().body();
            assertNotNull(rows); assertEquals(1, rows.size());
            assertEquals(1.25, rows.get(0).distanceKm, 0);
            assertEquals(6.9, rows.get(0).station.latitude, 0);
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(request);
            assertEquals("/api/v1/stations/nearby", request.getRequestUrl().encodedPath());
            assertEquals("6.91", request.getRequestUrl().queryParameter("latitude"));
            assertEquals("79.81", request.getRequestUrl().queryParameter("longitude"));
            assertEquals("25.0", request.getRequestUrl().queryParameter("radiusKm"));
        }
    }
    @Test public void slotDiscoveryRetainsIdsCountsAndUtcTimes() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("[{\"slotId\":\"slot\",\"stationId\":\"node\",\"startAtUtc\":\"2030-01-01T00:00:00Z\",\"endAtUtc\":\"2030-01-01T01:00:00Z\",\"totalSlots\":4,\"availableSlots\":0,\"isActive\":true}]"));
            List<SlotResponse> rows = api(server).stationSlots("node").execute().body();
            assertNotNull(rows); assertEquals("node", rows.get(0).stationId);
            assertEquals(0, rows.get(0).availableSlots); assertEquals(4, rows.get(0).totalSlots);
            assertEquals("2030-01-01T00:00:00Z", rows.get(0).startAtUtc);
            assertEquals("/api/v1/stations/node/slots", server.takeRequest(2, TimeUnit.SECONDS).getPath());
        }
    }
    @Test public void allStationsAndDetailRemainSeparateReadRequests() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("[]"));
            assertTrue(api(server).listStations().execute().body().isEmpty());
            assertEquals("/api/v1/stations", server.takeRequest(2, TimeUnit.SECONDS).getPath());
            server.enqueue(new MockResponse().setBody("{\"stationId\":\"node\",\"operatingSchedule\":[{\"day\":1,\"isClosed\":true,\"opensAt\":null,\"closesAt\":null}]}"));
            com.smartsolar.mobile.data.remote.dto.StationResponse station = api(server).getStation("node").execute().body();
            assertNotNull(station); assertEquals("node", station.stationId);
            assertTrue(station.operatingSchedule.get(0).isClosed); assertNull(station.operatingSchedule.get(0).opensAt);
            assertEquals("/api/v1/stations/node", server.takeRequest(2, TimeUnit.SECONDS).getPath());
        }
    }
}
