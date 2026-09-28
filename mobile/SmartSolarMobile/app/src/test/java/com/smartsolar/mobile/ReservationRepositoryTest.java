package com.smartsolar.mobile;

import com.smartsolar.mobile.data.remote.api.ApiService;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.repository.ReservationError;
import com.smartsolar.mobile.data.repository.ReservationRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

public class ReservationRepositoryTest {
    private MockWebServer server;
    private ApiService apiService;
    private ExecutorService directExecutor;
    private ReservationRepository repository;

    private static final String SAMPLE_RESERVATION_JSON = "{" +
            "\"reservationId\":\"res-uuid-1\"," +
            "\"prosumerNic\":\"200012345678\"," +
            "\"stationId\":\"sta-uuid-2\"," +
            "\"slotId\":\"11111111-1111-1111-1111-111111111111\"," +
            "\"energyAmountKwh\":2.5," +
            "\"scheduledStartAtUtc\":\"2026-09-30T10:00:00Z\"," +
            "\"scheduledEndAtUtc\":\"2026-09-30T11:00:00Z\"," +
            "\"status\":\"Pending\"," +
            "\"createdAtUtc\":\"2026-09-24T12:00:00Z\"," +
            "\"updatedAtUtc\":\"2026-09-24T12:00:00Z\"" +
            "}";

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        OkHttpClient client = new OkHttpClient.Builder().build();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(server.url("/api/v1/"))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
        directExecutor = Executors.newSingleThreadExecutor();
        repository = new ReservationRepository(apiService, directExecutor, null);
    }

    @After
    public void tearDown() throws Exception {
        repository.close();
        directExecutor.shutdownNow();
        server.shutdown();
    }

    @Test
    public void createReservationPostsExactPayloadAndReturnsResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(SAMPLE_RESERVATION_JSON));

        AtomicReference<ReservationResponse> resultRef = new AtomicReference<>();
        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.createReservation("11111111-1111-1111-1111-111111111111", 2.5, new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNull(errorRef.get());
        assertNotNull(resultRef.get());
        assertEquals("res-uuid-1", resultRef.get().getReservationId());
        assertEquals(2.5, resultRef.get().getEnergyAmountKwh(), 0.001);

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("POST", recorded.getMethod());
        assertEquals("/api/v1/reservations", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"slotId\":\"11111111-1111-1111-1111-111111111111\""));
        assertTrue(body.contains("\"energyAmountKwh\":2.5"));
    }

    @Test
    public void getReservationExecutesGetAndParsesResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(SAMPLE_RESERVATION_JSON));

        AtomicReference<ReservationResponse> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getReservation("res-uuid-1", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        assertEquals("res-uuid-1", resultRef.get().getReservationId());

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("GET", recorded.getMethod());
        assertEquals("/api/v1/reservations/res-uuid-1", recorded.getPath());
    }

    @Test
    public void updateReservationPutsPayloadAndReturnsUpdatedResponse() throws Exception {
        String updatedJson = SAMPLE_RESERVATION_JSON.replace("\"energyAmountKwh\":2.5", "\"energyAmountKwh\":4.0");
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(updatedJson));

        AtomicReference<ReservationResponse> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.updateReservation("res-uuid-1", "11111111-1111-1111-1111-111111111111", 4.0, new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        assertEquals(4.0, resultRef.get().getEnergyAmountKwh(), 0.001);

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("PUT", recorded.getMethod());
        assertEquals("/api/v1/reservations/res-uuid-1", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"energyAmountKwh\":4.0"));
    }

    @Test
    public void cancelReservationPatchesEndpointAndReturnsCancelledResponse() throws Exception {
        String cancelledJson = SAMPLE_RESERVATION_JSON.replace("\"status\":\"Pending\"", "\"status\":\"Cancelled\"");
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(cancelledJson));

        AtomicReference<ReservationResponse> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.cancelReservation("res-uuid-1", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        assertEquals("Cancelled", resultRef.get().getStatus());

        RecordedRequest recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("PATCH", recorded.getMethod());
        assertEquals("/api/v1/reservations/res-uuid-1/cancel", recorded.getPath());
    }

    @Test
    public void handles409ConflictWithProblemDetails() throws Exception {
        String problemJson = "{" +
                "\"type\":\"https://tools.ietf.org/html/rfc9110#section-15.5.10\"," +
                "\"title\":\"Conflict\"," +
                "\"status\":409," +
                "\"detail\":\"Reservation modifications require at least 12 hours notice.\"," +
                "\"traceId\":\"trace-conflict-409\"" +
                "}";

        server.enqueue(new MockResponse()
                .setResponseCode(409)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(problemJson));

        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.cancelReservation("res-uuid-1", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(409, errorRef.get().getStatusCode());
        assertEquals("Reservation modifications require at least 12 hours notice.", errorRef.get().getMessage());
        assertEquals("trace-conflict-409", errorRef.get().getTraceId());
        assertFalse(errorRef.get().isSessionExpired());
    }

    @Test
    public void handles401UnauthorizedAsSessionExpired() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{\"title\":\"Unauthorized\",\"status\":401}"));

        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getReservation("res-uuid-1", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(401, errorRef.get().getStatusCode());
        assertTrue(errorRef.get().isSessionExpired());
    }

    @Test
    public void handles404NotFound() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{\"title\":\"Not Found\",\"status\":404,\"detail\":\"Reservation not found.\"}"));

        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getReservation("res-missing", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(404, errorRef.get().getStatusCode());
        assertEquals("Reservation not found.", errorRef.get().getMessage());
    }

    @Test
    public void handlesNetworkFailure() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getReservation("res-uuid-1", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(0, errorRef.get().getStatusCode());
        assertTrue(errorRef.get().getMessage().contains("Unable to reach the server"));
    }

    @Test
    public void handles400BadRequestWithValidationErrors() throws Exception {
        String validationJson = "{" +
                "\"type\":\"https://tools.ietf.org/html/rfc9110#section-15.5.1\"," +
                "\"title\":\"Validation Failed\"," +
                "\"status\":400," +
                "\"errors\":{\"EnergyAmountKwh\":[\"EnergyAmountKwh must be greater than zero.\"]}" +
                "}";

        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/problem+json")
                .setBody(validationJson));

        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.createReservation("11111111-1111-1111-1111-111111111111", 0, new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(400, errorRef.get().getStatusCode());
        assertEquals("Validation Failed", errorRef.get().getMessage());
        assertNotNull(errorRef.get().getValidationErrors());
        assertTrue(errorRef.get().getValidationErrors().containsKey("EnergyAmountKwh"));
    }

    @Test
    public void handles403ForbiddenAccessDenied() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Access denied to this reservation.\"}"));

        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getReservation("res-other-user", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(403, errorRef.get().getStatusCode());
        assertEquals("Access denied to this reservation.", errorRef.get().getMessage());
    }

    @Test
    public void handles500ServerErrorFallback() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "text/plain")
                .setBody("Internal Server Error"));

        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getReservation("res-500", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(500, errorRef.get().getStatusCode());
        assertEquals("Request failed with status 500.", errorRef.get().getMessage());
    }

    @Test
    public void handlesMalformedProblemDetailsJsonGracefully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{bad-json-syntax"));

        AtomicReference<ReservationError> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getReservation("res-bad-json", new ReservationRepository.Callback<ReservationResponse>() {
            @Override
            public void onSuccess(ReservationResponse result) {
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertEquals(400, errorRef.get().getStatusCode());
        assertEquals("Invalid reservation request.", errorRef.get().getMessage());
    }

    @Test
    public void getMyReservationsGetsReservationsList() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[" + SAMPLE_RESERVATION_JSON + "]"));

        AtomicReference<java.util.List<ReservationResponse>> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getMyReservations(new ReservationRepository.Callback<java.util.List<ReservationResponse>>() {
            @Override
            public void onSuccess(java.util.List<ReservationResponse> result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("GET", recorded.getMethod());
        assertEquals("/api/v1/reservations/my", recorded.getPath());
        assertNotNull(resultRef.get());
        assertEquals(1, resultRef.get().size());
        assertEquals("res-uuid-1", resultRef.get().get(0).getReservationId());
    }

    @Test
    public void getAvailableSlotsGetsSlotsList() throws Exception {
        String slotJson = "{" +
                "\"slotId\":\"slot-111\"," +
                "\"stationId\":\"sta-222\"," +
                "\"startAtUtc\":\"2026-09-30T10:00:00Z\"," +
                "\"endAtUtc\":\"2026-09-30T11:00:00Z\"," +
                "\"availableSlots\":3," +
                "\"totalSlots\":5" +
                "}";

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[" + slotJson + "]"));

        AtomicReference<java.util.List<com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse>> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.getAvailableSlots(new ReservationRepository.Callback<java.util.List<com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse>>() {
            @Override
            public void onSuccess(java.util.List<com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse> result) {
                resultRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(ReservationError error) {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        RecordedRequest recorded = server.takeRequest();
        assertEquals("GET", recorded.getMethod());
        assertEquals("/api/v1/reservations/slots", recorded.getPath());
        assertNotNull(resultRef.get());
        assertEquals(1, resultRef.get().size());
        assertEquals("slot-111", resultRef.get().get(0).getSlotId());
    }
}

