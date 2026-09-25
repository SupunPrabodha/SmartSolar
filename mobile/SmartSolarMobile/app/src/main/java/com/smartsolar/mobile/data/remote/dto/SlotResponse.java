package com.smartsolar.mobile.data.remote.dto;
/** Read-only inventory contract; Android discovery has no booking command. */
public final class SlotResponse {
    public String slotId, stationId, startAtUtc, endAtUtc;
    public int totalSlots, availableSlots;
    public boolean isActive;
}
