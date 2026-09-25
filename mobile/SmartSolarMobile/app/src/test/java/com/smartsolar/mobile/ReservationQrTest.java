package com.smartsolar.mobile;

import com.google.gson.Gson;
import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationQrResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationVerificationResponse;
import com.smartsolar.mobile.data.remote.dto.VerifyReservationQrRequest;
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

public class ReservationQrTest {
    private final Gson gson = new Gson();
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
    public void deserializesReservationQrResponseCorrectly() {
        String json = "{\n" +
                "  \"reservationId\": \"res-abc-123\",\n" +
                "  \"qrPayload\": \"SMG1.dGVzdC10b2tlbi1yYW5kb20tYnl0ZXM\",\n" +
                "  \"issuedAtUtc\": \"2030-05-10T10:00:00Z\"\n" +
                "}";

        ReservationQrResponse dto = gson.fromJson(json, ReservationQrResponse.class);

        assertNotNull(dto);
        assertEquals("res-abc-123", dto.getReservationId());
        assertEquals("SMG1.dGVzdC10b2tlbi1yYW5kb20tYnl0ZXM", dto.getQrPayload());
        assertEquals("2030-05-10T10:00:00Z", dto.getIssuedAtUtc());
        assertTrue(dto.getQrPayload().startsWith("SMG1."));
    }

    @Test
    public void serializesVerifyReservationQrRequestCorrectly() {
        VerifyReservationQrRequest request = new VerifyReservationQrRequest("SMG1.opaque_secure_payload");
        String json = gson.toJson(request);

        assertTrue(json.contains("\"qrPayload\":\"SMG1.opaque_secure_payload\""));
    }

    @Test
    public void deserializesReservationVerificationResponseCorrectly() {
        String json = "{\n" +
                "  \"reservationId\": \"res-999\",\n" +
                "  \"status\": \"Approved\",\n" +
                "  \"prosumerNic\": \"200012345678\",\n" +
                "  \"prosumerName\": \"John Doe\",\n" +
                "  \"stationId\": \"station-east-01\",\n" +
                "  \"stationName\": \"East Charging Hub\",\n" +
                "  \"slotId\": \"slot-bay-4\",\n" +
                "  \"scheduledStartAtUtc\": \"2030-05-10T10:00:00Z\",\n" +
                "  \"scheduledEndAtUtc\": \"2030-05-10T11:00:00Z\",\n" +
                "  \"energyAmountKwh\": 25.0,\n" +
                "  \"qrIssuedAtUtc\": \"2030-05-10T09:30:00Z\",\n" +
                "  \"eligibleForCompletion\": true\n" +
                "}";

        ReservationVerificationResponse dto = gson.fromJson(json, ReservationVerificationResponse.class);

        assertNotNull(dto);
        assertEquals("res-999", dto.getReservationId());
        assertEquals("Approved", dto.getStatus());
        assertEquals("200012345678", dto.getProsumerNic());
        assertEquals("station-east-01", dto.getStationId());
        assertEquals("slot-bay-4", dto.getSlotId());
        assertEquals("2030-05-10T10:00:00Z", dto.getScheduledStartAtUtc());
        assertEquals("2030-05-10T11:00:00Z", dto.getScheduledEndAtUtc());
        assertEquals(25.0, dto.getEnergyAmountKwh(), 0.001);
        assertEquals("2030-05-10T09:30:00Z", dto.getQrIssuedAtUtc());
        assertTrue(dto.isEligibleForCompletion());
    }

    @Test
    public void issueQrHitsExpectedEndpoint() throws Exception {
        String json = "{\"reservationId\":\"res-123\",\"qrPayload\":\"SMG1.sample_payload\",\"issuedAtUtc\":\"2030-01-01T00:00:00Z\"}";
        server.enqueue(new MockResponse().setBody(json).setHeader("Content-Type", "application/json"));

        Response<ReservationQrResponse> response = api.issueQr("res-123").execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertEquals("res-123", response.body().getReservationId());
        assertEquals("SMG1.sample_payload", response.body().getQrPayload());

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/reservations/res-123/qr", request.getPath());
        assertEquals("POST", request.getMethod());
    }

    @Test
    public void verifyQrHitsExpectedEndpoint() throws Exception {
        String json = "{\"reservationId\":\"res-123\",\"status\":\"Approved\",\"prosumerNic\":\"200012345678\",\"eligibleForCompletion\":true}";
        server.enqueue(new MockResponse().setBody(json).setHeader("Content-Type", "application/json"));

        Response<ReservationVerificationResponse> response = api.verifyQr(new VerifyReservationQrRequest("SMG1.sample_payload")).execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertEquals("res-123", response.body().getReservationId());
        assertEquals("Approved", response.body().getStatus());

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/reservations/qr/verify", request.getPath());
        assertEquals("POST", request.getMethod());
        assertTrue(request.getBody().readUtf8().contains("SMG1.sample_payload"));
    }

    @Test
    public void serializesCompleteReservationTransferRequestCorrectly() {
        com.smartsolar.mobile.data.remote.dto.CompleteReservationTransferRequest request =
                new com.smartsolar.mobile.data.remote.dto.CompleteReservationTransferRequest("SMG1.sample_payload", "res-123");
        String json = gson.toJson(request);

        assertTrue(json.contains("\"qrPayload\":\"SMG1.sample_payload\""));
        assertTrue(json.contains("\"reservationId\":\"res-123\""));
    }

    @Test
    public void deserializesReservationCompletionResponseCorrectly() {
        String json = "{\n" +
                "  \"reservationId\": \"res-123\",\n" +
                "  \"prosumerNic\": \"200012345678\",\n" +
                "  \"stationId\": \"station-1\",\n" +
                "  \"slotId\": \"slot-1\",\n" +
                "  \"energyAmountKwh\": 15.5,\n" +
                "  \"scheduledStartAtUtc\": \"2030-05-10T10:00:00Z\",\n" +
                "  \"scheduledEndAtUtc\": \"2030-05-10T11:00:00Z\",\n" +
                "  \"status\": \"Completed\",\n" +
                "  \"completedAtUtc\": \"2030-05-10T10:15:00Z\",\n" +
                "  \"completedByOperatorNic\": \"199012345678\",\n" +
                "  \"updatedAtUtc\": \"2030-05-10T10:15:00Z\"\n" +
                "}";

        com.smartsolar.mobile.data.remote.dto.ReservationCompletionResponse dto =
                gson.fromJson(json, com.smartsolar.mobile.data.remote.dto.ReservationCompletionResponse.class);

        assertNotNull(dto);
        assertEquals("res-123", dto.getReservationId());
        assertEquals("Completed", dto.getStatus());
        assertEquals("199012345678", dto.getCompletedByOperatorNic());
        assertEquals("2030-05-10T10:15:00Z", dto.getCompletedAtUtc());
        assertEquals(15.5, dto.getEnergyAmountKwh(), 0.001);
    }

    @Test
    public void completeTransferHitsExpectedEndpoint() throws Exception {
        String json = "{\"reservationId\":\"res-123\",\"status\":\"Completed\",\"completedByOperatorNic\":\"199012345678\",\"completedAtUtc\":\"2030-05-10T10:15:00Z\"}";
        server.enqueue(new MockResponse().setBody(json).setHeader("Content-Type", "application/json"));

        Response<com.smartsolar.mobile.data.remote.dto.ReservationCompletionResponse> response =
                api.completeTransfer(new com.smartsolar.mobile.data.remote.dto.CompleteReservationTransferRequest("SMG1.sample_payload", "res-123")).execute();

        assertTrue(response.isSuccessful());
        assertNotNull(response.body());
        assertEquals("res-123", response.body().getReservationId());
        assertEquals("Completed", response.body().getStatus());

        RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/reservations/qr/complete", request.getPath());
        assertEquals("POST", request.getMethod());
        assertTrue(request.getBody().readUtf8().contains("SMG1.sample_payload"));
    }
}
