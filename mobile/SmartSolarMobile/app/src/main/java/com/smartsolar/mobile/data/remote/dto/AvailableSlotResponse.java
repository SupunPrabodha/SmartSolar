package com.smartsolar.mobile.data.remote.dto;

public final class AvailableSlotResponse {
    private String slotId;
    private String stationId;
    private String startAtUtc;
    private String endAtUtc;
    private int availableSlots;
    private int totalSlots;

    public AvailableSlotResponse() { }

    public AvailableSlotResponse(String slotId, String stationId, String startAtUtc, String endAtUtc, int availableSlots, int totalSlots) {
        this.slotId = slotId;
        this.stationId = stationId;
        this.startAtUtc = startAtUtc;
        this.endAtUtc = endAtUtc;
        this.availableSlots = availableSlots;
        this.totalSlots = totalSlots;
    }

    public String getSlotId() { return slotId; }
    public String getStationId() { return stationId; }
    public String getStartAtUtc() { return startAtUtc; }
    public String getEndAtUtc() { return endAtUtc; }
    public int getAvailableSlots() { return availableSlots; }
    public int getTotalSlots() { return totalSlots; }
}
