package com.smartsolar.mobile;

import com.google.gson.Gson;
import com.smartsolar.mobile.data.remote.dto.CreateReservationRequest;
import com.smartsolar.mobile.data.remote.dto.ProblemDetailsResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import com.smartsolar.mobile.data.remote.dto.UpdateReservationRequest;
import com.smartsolar.mobile.data.repository.ReservationError;
import org.junit.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ReservationDtoTest {
    private final Gson gson = new Gson();

    @Test
    public void serializesCreateReservationRequestWithExactApiFields() {
        CreateReservationRequest request = new CreateReservationRequest("11111111-1111-1111-1111-111111111111", 2.5);
        String json = gson.toJson(request);
        assertTrue(json.contains("\"slotId\":\"11111111-1111-1111-1111-111111111111\""));
        assertTrue(json.contains("\"energyAmountKwh\":2.5"));
        assertFalse(json.contains("status"));
        assertFalse(json.contains("prosumerNic"));
        assertFalse(json.contains("qrToken"));
    }

    @Test
    public void serializesUpdateReservationRequestWithExactApiFields() {
        UpdateReservationRequest request = new UpdateReservationRequest("22222222-2222-2222-2222-222222222222", 3.75);
        String json = gson.toJson(request);
        assertTrue(json.contains("\"slotId\":\"22222222-2222-2222-2222-222222222222\""));
        assertTrue(json.contains("\"energyAmountKwh\":3.75"));
    }

    @Test
    public void deserializesReservationResponseCorrectly() {
        String json = "{" +
                "\"reservationId\":\"res-123\"," +
                "\"prosumerNic\":\"200012345678\"," +
                "\"stationId\":\"sta-456\"," +
                "\"slotId\":\"slo-789\"," +
                "\"energyAmountKwh\":1.5," +
                "\"scheduledStartAtUtc\":\"2026-09-30T10:00:00Z\"," +
                "\"scheduledEndAtUtc\":\"2026-09-30T11:00:00Z\"," +
                "\"status\":\"Pending\"," +
                "\"createdAtUtc\":\"2026-09-24T12:00:00Z\"," +
                "\"updatedAtUtc\":\"2026-09-24T12:00:00Z\"" +
                "}";

        ReservationResponse response = gson.fromJson(json, ReservationResponse.class);
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
    }

    @Test
    public void deserializesProblemDetailsWithErrorsAndTraceId() {
        String json = "{" +
                "\"type\":\"https://tools.ietf.org/html/rfc9110#section-15.5.1\"," +
                "\"title\":\"Validation Failed\"," +
                "\"status\":400," +
                "\"detail\":\"One or more validation errors occurred.\"," +
                "\"instance\":\"/api/v1/reservations\"," +
                "\"traceId\":\"00-abcdef123456-00\"," +
                "\"errors\":{\"EnergyAmountKwh\":[\"EnergyAmountKwh must be greater than zero.\"]}" +
                "}";

        ProblemDetailsResponse problem = gson.fromJson(json, ProblemDetailsResponse.class);
        assertNotNull(problem);
        assertEquals(400, problem.getStatus());
        assertEquals("Validation Failed", problem.getTitle());
        assertEquals("One or more validation errors occurred.", problem.getDetail());
        assertEquals("00-abcdef123456-00", problem.getTraceId());
        assertNotNull(problem.getErrors());
        assertTrue(problem.getErrors().containsKey("EnergyAmountKwh"));
        assertEquals("EnergyAmountKwh must be greater than zero.", problem.getErrors().get("EnergyAmountKwh").get(0));
    }

    @Test
    public void reservationErrorHoldsStructuredDetails() {
        ReservationError error = new ReservationError(
                409,
                "The requested slot is already fully booked.",
                "trace-999",
                Collections.singletonMap("SlotId", List.of("Slot unavailable.")),
                false
        );
        assertEquals(409, error.getStatusCode());
        assertEquals("The requested slot is already fully booked.", error.getMessage());
        assertEquals("trace-999", error.getTraceId());
        assertEquals(1, error.getValidationErrors().size());
        assertFalse(error.isSessionExpired());
    }

    @Test
    public void deserializesAvailableSlotResponseCorrectly() {
        String json = "{" +
                "\"slotId\":\"slot-111\"," +
                "\"stationId\":\"sta-222\"," +
                "\"startAtUtc\":\"2026-09-30T10:00:00Z\"," +
                "\"endAtUtc\":\"2026-09-30T11:00:00Z\"," +
                "\"availableSlots\":3," +
                "\"totalSlots\":5" +
                "}";

        com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse slot =
                gson.fromJson(json, com.smartsolar.mobile.data.remote.dto.AvailableSlotResponse.class);
        assertNotNull(slot);
        assertEquals("slot-111", slot.getSlotId());
        assertEquals("sta-222", slot.getStationId());
        assertEquals("2026-09-30T10:00:00Z", slot.getStartAtUtc());
        assertEquals("2026-09-30T11:00:00Z", slot.getEndAtUtc());
        assertEquals(3, slot.getAvailableSlots());
        assertEquals(5, slot.getTotalSlots());
    }
}
