package com.smartsolar.mobile.data.remote.dto;
import java.util.List;
/** Read-only API DTO. Coordinates and capacity originate in the REST service. */
public final class StationResponse {
    public String stationId, name, address, updatedAtUtc;
    public double latitude, longitude, capacityKwh;
    public int totalBatterySlots;
    public boolean isActive;
    public List<OperatingDay> operatingSchedule;
    public static final class OperatingDay {
        public int day;
        public boolean isClosed;
        public String opensAt, closesAt;
    }
}
