package com.smartsolar.mobile.data.remote.dto;

public class UpdateReservationRequest {
    private final String slotId;
    private final double energyAmountKwh;

    public UpdateReservationRequest(String slotId, double energyAmountKwh) {
        this.slotId = slotId;
        this.energyAmountKwh = energyAmountKwh;
    }

    public String getSlotId() { return slotId; }
    public double getEnergyAmountKwh() { return energyAmountKwh; }
}
