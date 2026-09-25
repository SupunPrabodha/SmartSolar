package com.smartsolar.mobile;

import com.google.gson.Gson;
import com.smartsolar.mobile.data.remote.dto.ReservationDashboardSummaryResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationPageResponse;
import com.smartsolar.mobile.data.remote.dto.ReservationResponse;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReservationDtoTest {
    private final Gson gson = new Gson();

    @Test
    public void deserializesReservationResponseCorrectly() {
        String json = "{\n" +
                "  \"reservationId\": \"res-123\",\n" +
                "  \"prosumerNic\": \"200012345678\",\n" +
                "  \"stationId\": \"station-abc\",\n" +
                "  \"slotId\": \"slot-xyz\",\n" +
                "  \"energyAmountKwh\": 12.5,\n" +
                "  \"scheduledStartAtUtc\": \"2030-05-10T10:00:00Z\",\n" +
                "  \"scheduledEndAtUtc\": \"2030-05-10T11:00:00Z\",\n" +
                "  \"status\": \"Pending\",\n" +
                "  \"createdAtUtc\": \"2030-05-01T00:00:00Z\",\n" +
                "  \"updatedAtUtc\": \"2030-05-01T00:00:00Z\"\n" +
                "}";

        ReservationResponse dto = gson.fromJson(json, ReservationResponse.class);

        assertNotNull(dto);
        assertEquals("res-123", dto.getReservationId());
        assertEquals("200012345678", dto.getProsumerNic());
        assertEquals("station-abc", dto.getStationId());
        assertEquals("slot-xyz", dto.getSlotId());
        assertEquals(12.5, dto.getEnergyAmountKwh(), 0.001);
        assertEquals("2030-05-10T10:00:00Z", dto.getScheduledStartAtUtc());
        assertEquals("2030-05-10T11:00:00Z", dto.getScheduledEndAtUtc());
        assertEquals("Pending", dto.getStatus());
        assertEquals("2030-05-01T00:00:00Z", dto.getCreatedAtUtc());
        assertEquals("2030-05-01T00:00:00Z", dto.getUpdatedAtUtc());
    }

    @Test
    public void deserializesReservationPageResponseCorrectly() {
        String json = "{\n" +
                "  \"items\": [\n" +
                "    {\n" +
                "      \"reservationId\": \"res-1\",\n" +
                "      \"prosumerNic\": \"200012345678\",\n" +
                "      \"stationId\": \"sta-1\",\n" +
                "      \"slotId\": \"slo-1\",\n" +
                "      \"energyAmountKwh\": 5.0,\n" +
                "      \"scheduledStartAtUtc\": \"2030-01-01T00:00:00Z\",\n" +
                "      \"scheduledEndAtUtc\": \"2030-01-01T01:00:00Z\",\n" +
                "      \"status\": \"Approved\",\n" +
                "      \"createdAtUtc\": \"2029-12-31T00:00:00Z\",\n" +
                "      \"updatedAtUtc\": \"2029-12-31T00:00:00Z\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"page\": 2,\n" +
                "  \"pageSize\": 10,\n" +
                "  \"hasMore\": true\n" +
                "}";

        ReservationPageResponse page = gson.fromJson(json, ReservationPageResponse.class);

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
        String json = "{\n" +
                "  \"pendingReservations\": 5,\n" +
                "  \"approvedFutureReservations\": 18,\n" +
                "  \"generatedAtUtc\": \"2030-05-10T08:00:00Z\"\n" +
                "}";

        ReservationDashboardSummaryResponse summary =
                gson.fromJson(json, ReservationDashboardSummaryResponse.class);

        assertNotNull(summary);
        assertEquals(5, summary.getPendingReservations());
        assertEquals(18, summary.getApprovedFutureReservations());
        assertEquals("2030-05-10T08:00:00Z", summary.getGeneratedAtUtc());
    }
}
