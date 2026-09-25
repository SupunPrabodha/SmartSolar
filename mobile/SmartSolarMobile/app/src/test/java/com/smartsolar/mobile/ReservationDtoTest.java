package com.smartsolar.mobile;

import com.google.gson.Gson;
import com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse;
import com.smartsolar.mobile.data.remote.dto.CreateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.ProblemDetailsResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationDashboardSummaryResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.UpdateReservationRequest;
import com.smartsolar.mobile.data.repository.ReservationError;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class ReservationDtoTest {
    private final Gson gson = new Gson();

    @Test
    public void serializesCreateReservationRequestWithExactApiFields() {
        CreateReservationRequest request = new CreateReservationRequest(
                "11111111-1111-1111-1111-111111111111",
                2.5
        );

        String json = gson.toJson(request);

        assertTrue(json.contains("\"slotId\":\"11111111-1111-1111-1111-111111111111\""));
        assertTrue(json.contains("\"energyAmountKwh\":2.5"));
        assertFalse(json.contains("status"));
        assertFalse(json.contains("prosumerNic"));
        assertFalse(json.contains("qrToken"));
    }

    @Test
    public void serializesUpdateReservationRequestWithExactApiFields() {
        UpdateReservationRequest request = new UpdateReservationRequest(
                "22222222-2222-2222-2222-222222222222",
                3.75
        );

        String json = gson.toJson(request);

        assertTrue(json.contains("\"slotId\":\"22222222-2222-2222-2222-222222222222\""));
        assertTrue(json.contains("\"energyAmountKwh\":3.75"));
    }

    @Test
    public void deserializesReservationResponseCorrectly() {
        String json = "{"
                + "\"reservationId\":\"res-123\","
                + "\"prosumerNic\":\"200012345678\","
                + "\"stationId\":\"sta-456\","
                + "\"slotId\":\"slo-789\","
                + "\"energyAmountKwh\":1.5,"
                + "\"scheduledStartAtUtc\":\"2026-09-30T10:00:00Z\","
                + "\"scheduledEndAtUtc\":\"2026-09-30T11:00:00Z\","
                + "\"status\":\"Pending\","
                + "\"createdAtUtc\":\"2026-09-24T12:00:00Z\","
                + "\"updatedAtUtc\":\"2026-09-24T12:00:00Z\""
                + "}";

        ReservationResponse response =
                gson.fromJson(json, ReservationResponse.class);

        assertNotNull(response);
        assertEquals("res-123", response.getReservationId());
        assertEquals("200012345678", response.getProsumerNic());
        assertEquals("sta-456", response.getStationId());
        assertEquals("slo-789", response.getSlotId());
        assertEquals(1.5, response.getEnergyAmountKwh(), 0.001);
        assertEquals("2026-09-30T10:00:00Z", response.getScheduledStartAtUtc());
        assertEquals("2026-09-30T11:00:00Z", response.getScheduledEndAtUtc());
        assertEquals("Pending", response.getStatus());
        assertEquals("2026-09-24T12:00:00Z", response.getCreatedAtUtc());
        assertEquals("2026-09-24T12:00:00Z", response.getUpdatedAtUtc());
        assertNull(response.getRejectionRemark());
    }

    @Test
    public void deserializesRejectedReservationResponseWithRemark() {
        String json = "{" +
                "\"reservationId\":\"res-123\"," +
                "\"prosumerNic\":\"200012345678\"," +
                "\"stationId\":\"sta-456\"," +
                "\"slotId\":\"slo-789\"," +
                "\"energyAmountKwh\":1.5," +
                "\"scheduledStartAtUtc\":\"2026-09-30T10:00:00Z\"," +
                "\"scheduledEndAtUtc\":\"2026-09-30T11:00:00Z\"," +
                "\"status\":\"Rejected\"," +
                "\"createdAtUtc\":\"2026-09-24T12:00:00Z\"," +
                "\"updatedAtUtc\":\"2026-09-24T12:00:00Z\"," +
                "\"rejectionRemark\":\"Station undergoing routine grid maintenance.\"" +
                "}";

        ReservationResponse response = gson.fromJson(json, ReservationResponse.class);
        assertNotNull(response);
        assertEquals("Rejected", response.getStatus());
        assertEquals("Station undergoing routine grid maintenance.", response.getRejectionRemark());
    }

    @Test
    public void deserializesReservationPageResponseCorrectly() {
        String json = "{"
                + "\"items\":[{"
                + "\"reservationId\":\"res-1\","
                + "\"prosumerNic\":\"200012345678\","
                + "\"stationId\":\"sta-1\","
                + "\"slotId\":\"slo-1\","
                + "\"energyAmountKwh\":5.0,"
                + "\"scheduledStartAtUtc\":\"2030-01-01T00:00:00Z\","
                + "\"scheduledEndAtUtc\":\"2030-01-01T01:00:00Z\","
                + "\"status\":\"Approved\","
                + "\"createdAtUtc\":\"2029-12-31T00:00:00Z\","
                + "\"updatedAtUtc\":\"2029-12-31T00:00:00Z\""
                + "}],"
                + "\"page\":2,"
                + "\"pageSize\":10,"
                + "\"hasMore\":true"
                + "}";

        ReservationPageResponse page =
                gson.fromJson(json, ReservationPageResponse.class);

        assertNotNull(page);
        assertEquals(2, page.getPage());
        assertEquals(10, page.getPageSize());
        assertTrue(page.isHasMore());
        assertEquals(1, page.getItems().size());
        assertEquals("res-1", page.getItems().get(0).getReservationId());
        assertEquals("Approved", page.getItems().get(0).getStatus());
    }

    @Test
    public void deserializesReservationDashboardSummaryResponseCorrectly() {
        String json = "{"
                + "\"pendingReservations\":5,"
                + "\"approvedFutureReservations\":18,"
                + "\"generatedAtUtc\":\"2030-05-10T08:00:00Z\""
                + "}";

        ReservationDashboardSummaryResponse summary =
                gson.fromJson(json, ReservationDashboardSummaryResponse.class);

        assertNotNull(summary);
        assertEquals(5, summary.getPendingReservations());
        assertEquals(18, summary.getApprovedFutureReservations());
        assertEquals("2030-05-10T08:00:00Z", summary.getGeneratedAtUtc());
    }

    @Test
    public void deserializesProblemDetailsWithErrorsAndTraceId() {
        String json = "{"
                + "\"type\":\"https://tools.ietf.org/html/rfc9110#section-15.5.1\","
                + "\"title\":\"Validation Failed\","
                + "\"status\":400,"
                + "\"detail\":\"One or more validation errors occurred.\","
                + "\"instance\":\"/api/v1/reservations\","
                + "\"traceId\":\"00-abcdef123456-00\","
                + "\"errors\":{\"EnergyAmountKwh\":["
                + "\"EnergyAmountKwh must be greater than zero.\"]}"
                + "}";

        ProblemDetailsResponse problem =
                gson.fromJson(json, ProblemDetailsResponse.class);

        assertNotNull(problem);
        assertEquals(400, problem.getStatus());
        assertEquals("Validation Failed", problem.getTitle());
        assertEquals(
                "One or more validation errors occurred.",
                problem.getDetail()
        );
        assertEquals("00-abcdef123456-00", problem.getTraceId());
        assertNotNull(problem.getErrors());
        assertTrue(problem.getErrors().containsKey("EnergyAmountKwh"));
        assertEquals(
                "EnergyAmountKwh must be greater than zero.",
                problem.getErrors().get("EnergyAmountKwh").get(0)
        );
    }

    @Test
    public void reservationErrorHoldsStructuredDetails() {
        ReservationError error = new ReservationError(
                409,
                "The requested slot is already fully booked.",
                "trace-999",
                Collections.singletonMap(
                        "SlotId",
                        Collections.singletonList("Slot unavailable.")
                ),
                false
        );

        assertEquals(409, error.getStatusCode());
        assertEquals(
                "The requested slot is already fully booked.",
                error.getMessage()
        );
        assertEquals("trace-999", error.getTraceId());
        assertEquals(1, error.getValidationErrors().size());
        assertFalse(error.isSessionExpired());
    }

    @Test
    public void deserializesAvailableSlotResponseCorrectly() {
        String json = "{"
                + "\"slotId\":\"slot-111\","
                + "\"stationId\":\"sta-222\","
                + "\"startAtUtc\":\"2026-09-30T10:00:00Z\","
                + "\"endAtUtc\":\"2026-09-30T11:00:00Z\","
                + "\"availableSlots\":3,"
                + "\"totalSlots\":5"
                + "}";

        AvailableSlotResponse slot =
                gson.fromJson(json, AvailableSlotResponse.class);

        assertNotNull(slot);
        assertEquals("slot-111", slot.getSlotId());
        assertEquals("sta-222", slot.getStationId());
        assertEquals("2026-09-30T10:00:00Z", slot.getStartAtUtc());
        assertEquals("2026-09-30T11:00:00Z", slot.getEndAtUtc());
        assertEquals(3, slot.getAvailableSlots());
        assertEquals(5, slot.getTotalSlots());
    }
}